//! Cloudflare Turnstile verification for the public auth surfaces.
//!
//! Turnstile is the bot control in front of `POST /auth/login` and
//! `POST /auth/register`. Those are rate limited per IP, but a credential
//! stuffing run distributed across a botnet never trips a per-IP bucket, so the
//! challenge is what actually raises the cost of the attack.
//!
//! Two properties matter more than the happy path:
//!
//! * **Fail closed while configured.** Once `TURNSTILE_ENABLED=true` and both
//!   keys are present, a request without a valid token is rejected. A network
//!   failure reaching Cloudflare is a 503, never a silent pass — an attacker who
//!   can black-hole `challenges.cloudflare.com` must not thereby switch the
//!   protection off.
//! * **Operator errors never read as user errors.** A wrong or duplicated secret
//!   key comes back from siteverify as `invalid-input-secret`. That is a
//!   deployment fault, so it is logged loudly and surfaced as 503, not as "you
//!   failed the challenge" — which would otherwise present as every user on the
//!   site suddenly being unable to log in for no visible reason.

use crate::core::config;
use crate::core::error::ApiError;
use serde::Deserialize;
use std::net::IpAddr;
use std::sync::OnceLock;
use std::time::Duration;

static TURNSTILE_HTTP_CLIENT: OnceLock<Result<reqwest::Client, String>> = OnceLock::new();

/// Header carrying the widget's token. Named after Cloudflare's own form field
/// (`cf-turnstile-response`) so the value is recognisable in a request dump.
/// A header rather than a body field keeps every request model and handler
/// signature untouched, and lets one guard cover endpoints whose bodies differ.
pub const TURNSTILE_HEADER: &str = "cf-turnstile-response";

/// The siteverify payload. Cloudflare spells the failure list `error-codes`.
#[derive(Debug, Deserialize)]
struct SiteVerifyResponse {
    success: bool,
    #[serde(default, rename = "error-codes")]
    error_codes: Vec<String>,
    #[serde(default)]
    action: Option<String>,
    #[serde(default)]
    hostname: Option<String>,
}

/// Codes that mean *this deployment is misconfigured*, not *this visitor failed*.
const OPERATOR_FAULT_CODES: [&str; 3] = [
    "missing-input-secret",
    "invalid-input-secret",
    "bad-request",
];

fn turnstile_http_client() -> Result<&'static reqwest::Client, ApiError> {
    TURNSTILE_HTTP_CLIENT
        .get_or_init(|| {
            reqwest::Client::builder()
                .redirect(reqwest::redirect::Policy::none())
                .timeout(Duration::from_secs(10))
                .build()
                .map_err(|_| "Turnstile HTTP client initialization failed".to_string())
        })
        .as_ref()
        .map_err(|e| {
            log::error!("Turnstile client unavailable: {e}");
            ApiError::ServiceUnavailable(unavailable_message())
        })
}

fn unavailable_message() -> String {
    "Verification is temporarily unavailable. Please try again shortly.".to_string()
}

fn challenge_failed_message() -> String {
    "Verification failed. Please complete the challenge again.".to_string()
}

/// Reads the widget token out of the request headers, if present and non-empty.
pub fn token_from_headers(headers: &axum::http::HeaderMap) -> Option<&str> {
    headers
        .get(TURNSTILE_HEADER)
        .and_then(|value| value.to_str().ok())
        .map(str::trim)
        .filter(|value| !value.is_empty())
}

/// Verifies the Turnstile token guarding a public endpoint.
///
/// A no-op when Turnstile is not configured, which is what keeps local
/// development, the test suite, and desktop mode working unchanged. Once it IS
/// configured this is the gate: every outcome other than a Cloudflare `success`
/// is an error.
pub async fn verify_request(
    headers: &axum::http::HeaderMap,
    remote_ip: IpAddr,
    action: &str,
) -> Result<(), ApiError> {
    let turnstile = &config::get().turnstile;
    let Some(secret) = turnstile.active_secret() else {
        return Ok(());
    };
    let Some(token) = token_from_headers(headers) else {
        // A request with no token at all is the signature of frontend/backend
        // drift, not of a bot: the widget always attaches one when the bundle
        // was built with a site key. If EVERY login is landing here, the
        // frontend image was built without VITE_TURNSTILE_SITE_KEY while the
        // backend has Turnstile enabled — which locks out every real user, so
        // it is worth a loud, specific line rather than a generic 400.
        log::warn!(
            "Turnstile: {action} request carried no {TURNSTILE_HEADER} header. If this is \
             every request, rebuild the frontend image with VITE_TURNSTILE_SITE_KEY set to \
             the same site key as the backend's TURNSTILE_SITE_KEY."
        );
        return Err(ApiError::BadRequest(
            "Please complete the verification challenge.".to_string(),
        ));
    };

    // Cloudflare caps the token at 2048 bytes; anything longer is not a token
    // we issued, so reject it before spending a network round trip on it.
    if token.len() > 2048 {
        return Err(ApiError::BadRequest(challenge_failed_message()));
    }

    let client = turnstile_http_client()?;
    let response = client
        .post(&turnstile.verify_url)
        .form(&[
            ("secret", secret),
            ("response", token),
            ("remoteip", &remote_ip.to_string()),
        ])
        .send()
        .await
        .map_err(|e| {
            // Fail closed: an unreachable Cloudflare must not open the endpoint.
            log::error!("Turnstile siteverify request failed for {action}: {e}");
            ApiError::ServiceUnavailable(unavailable_message())
        })?;

    let status = response.status();
    if !status.is_success() {
        let body = response.text().await.unwrap_or_default();
        log::error!("Turnstile siteverify returned {status} for {action}: {body}");
        return Err(ApiError::ServiceUnavailable(unavailable_message()));
    }

    let verdict: SiteVerifyResponse = response.json().await.map_err(|e| {
        log::error!("Turnstile siteverify decode failed for {action}: {e}");
        ApiError::ServiceUnavailable(unavailable_message())
    })?;

    validate_verdict(&verdict, action, &turnstile.allowed_hostnames)
}

fn validate_verdict(
    verdict: &SiteVerifyResponse,
    expected_action: &str,
    allowed_hostnames: &[String],
) -> Result<(), ApiError> {
    if !verdict.success {
        return classify_failure(&verdict.error_codes, expected_action);
    }

    let action_matches = verdict
        .action
        .as_deref()
        .map(str::trim)
        .is_some_and(|actual| !actual.is_empty() && actual == expected_action);
    if !action_matches {
        log::warn!(
            "Turnstile action mismatch: expected={expected_action} actual={:?}",
            verdict.action
        );
        return Err(ApiError::BadRequest(challenge_failed_message()));
    }

    if !allowed_hostnames.is_empty() {
        let hostname = verdict
            .hostname
            .as_deref()
            .map(|h| h.trim().to_ascii_lowercase())
            .filter(|h| !h.is_empty());
        let host_allowed = hostname
            .as_deref()
            .is_some_and(|host| allowed_hostnames.iter().any(|allowed| allowed == host));
        if !host_allowed {
            log::warn!(
                "Turnstile hostname mismatch for {expected_action}: actual={:?} allowed={:?}",
                verdict.hostname,
                allowed_hostnames
            );
            return Err(ApiError::BadRequest(challenge_failed_message()));
        }
    }

    Ok(())
}

/// Splits a siteverify rejection into "the operator broke it" (503, logged) and
/// "the visitor failed or replayed the challenge" (400).
fn classify_failure(error_codes: &[String], action: &str) -> Result<(), ApiError> {
    let operator_fault = error_codes
        .iter()
        .any(|code| OPERATOR_FAULT_CODES.contains(&code.as_str()));

    if operator_fault {
        log::error!(
            "Turnstile is misconfigured — siteverify rejected the secret key for {action}: {}. \
             Check TURNSTILE_SECRET_KEY; it must be the SECRET half from the Cloudflare \
             dashboard, not a second copy of the site key.",
            error_codes.join(", ")
        );
        return Err(ApiError::ServiceUnavailable(unavailable_message()));
    }

    log::warn!(
        "Turnstile challenge rejected for {action}: {}",
        if error_codes.is_empty() {
            "no error code".to_string()
        } else {
            error_codes.join(", ")
        }
    );
    Err(ApiError::BadRequest(challenge_failed_message()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::http::HeaderMap;

    fn codes(values: &[&str]) -> Vec<String> {
        values.iter().map(|v| v.to_string()).collect()
    }

    #[test]
    fn missing_header_yields_no_token() {
        assert!(token_from_headers(&HeaderMap::new()).is_none());
    }

    #[test]
    fn blank_header_yields_no_token() {
        let mut headers = HeaderMap::new();
        headers.insert(TURNSTILE_HEADER, "   ".parse().unwrap());
        assert!(token_from_headers(&headers).is_none());
    }

    #[test]
    fn token_is_trimmed() {
        let mut headers = HeaderMap::new();
        headers.insert(TURNSTILE_HEADER, " abc123 ".parse().unwrap());
        assert_eq!(token_from_headers(&headers), Some("abc123"));
    }

    #[test]
    fn bad_secret_is_service_unavailable_not_bad_request() {
        // The whole point: a duplicated/incorrect secret key must not tell
        // every visitor that THEY failed the challenge.
        let err = classify_failure(&codes(&["invalid-input-secret"]), "login").unwrap_err();
        assert!(
            matches!(err, ApiError::ServiceUnavailable(_)),
            "expected 503 for an operator fault, got {err:?}"
        );
    }

    #[test]
    fn missing_secret_is_service_unavailable() {
        let err = classify_failure(&codes(&["missing-input-secret"]), "register").unwrap_err();
        assert!(matches!(err, ApiError::ServiceUnavailable(_)));
    }

    #[test]
    fn invalid_token_is_bad_request() {
        let err = classify_failure(&codes(&["invalid-input-response"]), "login").unwrap_err();
        assert!(
            matches!(err, ApiError::BadRequest(_)),
            "expected 400 for a visitor-side failure, got {err:?}"
        );
    }

    #[test]
    fn replayed_token_is_bad_request() {
        let err = classify_failure(&codes(&["timeout-or-duplicate"]), "login").unwrap_err();
        assert!(matches!(err, ApiError::BadRequest(_)));
    }

    #[test]
    fn empty_code_list_still_fails() {
        let err = classify_failure(&[], "login").unwrap_err();
        assert!(matches!(err, ApiError::BadRequest(_)));
    }

    #[test]
    fn operator_fault_wins_over_visitor_fault() {
        let err = classify_failure(
            &codes(&["invalid-input-response", "invalid-input-secret"]),
            "login",
        )
        .unwrap_err();
        assert!(matches!(err, ApiError::ServiceUnavailable(_)));
    }

    #[test]
    fn validate_verdict_enforces_action_and_hostname() {
        let allowed = vec!["hotel.example.com".to_string()];
        let ok = SiteVerifyResponse {
            success: true,
            error_codes: vec![],
            action: Some("login".to_string()),
            hostname: Some("Hotel.Example.Com".to_string()),
        };
        assert!(validate_verdict(&ok, "login", &allowed).is_ok());

        let wrong_action = SiteVerifyResponse {
            action: Some("register".to_string()),
            ..ok
        };
        assert!(matches!(
            validate_verdict(&wrong_action, "login", &allowed),
            Err(ApiError::BadRequest(_))
        ));

        let missing_action = SiteVerifyResponse {
            success: true,
            error_codes: vec![],
            action: None,
            hostname: Some("hotel.example.com".to_string()),
        };
        assert!(matches!(
            validate_verdict(&missing_action, "login", &allowed),
            Err(ApiError::BadRequest(_))
        ));

        let wrong_host = SiteVerifyResponse {
            success: true,
            error_codes: vec![],
            action: Some("login".to_string()),
            hostname: Some("evil.example.net".to_string()),
        };
        assert!(matches!(
            validate_verdict(&wrong_host, "login", &allowed),
            Err(ApiError::BadRequest(_))
        ));

        let missing_host = SiteVerifyResponse {
            hostname: None,
            ..wrong_host
        };
        assert!(matches!(
            validate_verdict(&missing_host, "login", &allowed),
            Err(ApiError::BadRequest(_))
        ));
    }
}
