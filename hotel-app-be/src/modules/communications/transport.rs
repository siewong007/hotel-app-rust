//! Email transport abstraction.
//!
//! `Transport::from_env()` builds the configured provider (SMTP via lettre);
//! exclusively from environment variables — never from system_settings or any
//! client-visible surface.

use lettre::message::{Attachment as MimeAttachment, Mailbox, MultiPart, SinglePart};
use lettre::transport::smtp::authentication::Credentials;
use lettre::{AsyncSmtpTransport, AsyncTransport, Message, Tokio1Executor};

use crate::core::error::ApiError;

#[derive(Debug, Clone)]
pub struct OutgoingEmail {
    pub to: String,
    pub subject: String,
    pub body_html: String,
    pub body_text: Option<String>,
    /// Files sent alongside the body (the checkout receipt's PDF copy).
    pub attachments: Vec<EmailAttachment>,
}

#[derive(Debug, Clone)]
pub struct EmailAttachment {
    pub filename: String,
    pub content_type: String,
    pub bytes: Vec<u8>,
}

/// Build the MIME message: the plain/HTML alternative alone, or wrapped in
/// `multipart/mixed` with the attachments after it.
fn build_message(from: Mailbox, email: &OutgoingEmail) -> Result<Message, String> {
    let to: Mailbox = email
        .to
        .parse()
        .map_err(|_| "invalid recipient address".to_string())?;
    let safe_subject = crate::utils::sanitization::Sanitizer::sanitize_header_line(&email.subject);
    let builder = Message::builder().from(from).to(to).subject(safe_subject);
    let html_part = || {
        SinglePart::builder()
            .header(lettre::message::header::ContentType::TEXT_HTML)
            .body(email.body_html.clone())
    };
    let message = if email.attachments.is_empty() {
        match &email.body_text {
            Some(text) => builder.multipart(MultiPart::alternative_plain_html(
                text.clone(),
                email.body_html.clone(),
            )),
            None => builder
                .header(lettre::message::header::ContentType::TEXT_HTML)
                .body(email.body_html.clone()),
        }
    } else {
        let mut mixed = match &email.body_text {
            Some(text) => MultiPart::mixed().multipart(MultiPart::alternative_plain_html(
                text.clone(),
                email.body_html.clone(),
            )),
            None => MultiPart::mixed().singlepart(html_part()),
        };
        for attachment in &email.attachments {
            let content_type =
                lettre::message::header::ContentType::parse(&attachment.content_type)
                    .map_err(|_| format!("invalid attachment type {}", attachment.content_type))?;
            let safe_filename =
                crate::utils::sanitization::Sanitizer::sanitize_header_line(&attachment.filename);
            mixed = mixed.singlepart(
                MimeAttachment::new(safe_filename).body(attachment.bytes.clone(), content_type),
            );
        }
        builder.multipart(mixed)
    };
    message.map_err(|e| format!("message build failed: {e}"))
}

#[derive(Debug, Clone)]
pub struct SmtpConfig {
    pub host: String,
    pub port: u16,
    pub username: Option<String>,
    pub password: Option<String>,
    pub from_email: String,
    pub from_name: Option<String>,
    /// "starttls" (default), "tls", or "none" (plain; local relays only).
    pub security: String,
}

impl SmtpConfig {
    /// None when SMTP_HOST or SMTP_FROM_EMAIL is unset/blank.
    pub fn from_env() -> Option<Self> {
        Self::from_values(|key| std::env::var(key).ok())
    }

    fn from_values(get: impl Fn(&str) -> Option<String>) -> Option<Self> {
        let env = |key: &str| get(key).filter(|value| !value.trim().is_empty());
        Some(Self {
            host: env("SMTP_HOST")?,
            port: env("SMTP_PORT").and_then(|v| v.parse().ok()).unwrap_or(587),
            username: env("SMTP_USERNAME"),
            password: env("SMTP_PASSWORD"),
            from_email: env("SMTP_FROM_EMAIL")?,
            from_name: env("SMTP_FROM_NAME"),
            security: env("SMTP_SECURITY").unwrap_or_else(|| "starttls".to_string()),
        })
    }
}

pub enum Transport {
    Smtp(Box<SmtpMailer>),
}

pub struct SmtpMailer {
    mailer: AsyncSmtpTransport<Tokio1Executor>,
    from: Mailbox,
}

impl SmtpMailer {
    fn build(config: &SmtpConfig) -> Result<Self, ApiError> {
        let internal =
            |m: String| ApiError::Internal(format!("SMTP transport configuration error: {m}"));
        let mut builder = match config.security.as_str() {
            "tls" => AsyncSmtpTransport::<Tokio1Executor>::relay(&config.host)
                .map_err(|e| internal(e.to_string()))?,
            "none" => AsyncSmtpTransport::<Tokio1Executor>::builder_dangerous(&config.host),
            _ => AsyncSmtpTransport::<Tokio1Executor>::starttls_relay(&config.host)
                .map_err(|e| internal(e.to_string()))?,
        }
        .port(config.port);
        if let (Some(user), Some(pass)) = (&config.username, &config.password) {
            builder = builder.credentials(Credentials::new(user.clone(), pass.clone()));
        }
        let from: Mailbox = match &config.from_name {
            Some(name) => format!("{name} <{}>", config.from_email),
            None => config.from_email.clone(),
        }
        .parse()
        .map_err(|_| internal("invalid SMTP_FROM_EMAIL / SMTP_FROM_NAME".to_string()))?;
        Ok(Self {
            mailer: builder.build(),
            from,
        })
    }
}

impl Transport {
    /// The configured production transport, or None when SMTP is not set up.
    pub fn from_env() -> Result<Option<Self>, ApiError> {
        match SmtpConfig::from_env() {
            Some(config) => Ok(Some(Transport::Smtp(Box::new(SmtpMailer::build(&config)?)))),
            None => Ok(None),
        }
    }

    /// Sends one message; returns a provider message id when available.
    /// Errors are returned as strings so callers can persist them as
    /// `last_error` without leaking typed internals.
    pub async fn send(&self, email: &OutgoingEmail) -> Result<Option<String>, String> {
        match self {
            Transport::Smtp(smtp) => {
                let message = build_message(smtp.from.clone(), email)?;
                let response = smtp
                    .mailer
                    .send(message)
                    .await
                    .map_err(|e| format!("smtp send failed: {e}"))?;
                Ok(response.message().next().map(ToOwned::to_owned))
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::{EmailAttachment, OutgoingEmail, SmtpConfig, build_message};

    fn email(attachments: Vec<EmailAttachment>) -> OutgoingEmail {
        OutgoingEmail {
            to: "guest@example.com".to_string(),
            subject: "Your receipt".to_string(),
            body_html: "<p>Thank you</p>".to_string(),
            body_text: Some("Thank you".to_string()),
            attachments,
        }
    }

    fn formatted(email: &OutgoingEmail) -> String {
        let from = "Hotel <hotel@example.com>".parse().expect("sender parses");
        let message = build_message(from, email).expect("message builds");
        String::from_utf8_lossy(&message.formatted()).into_owned()
    }

    #[test]
    fn attaches_the_receipt_pdf_beside_the_plain_and_html_body() {
        let raw = formatted(&email(vec![EmailAttachment {
            filename: "receipt-INV-1.pdf".to_string(),
            content_type: "application/pdf".to_string(),
            bytes: b"%PDF-1.7 test".to_vec(),
        }]));
        assert!(raw.contains("multipart/mixed"), "{raw}");
        assert!(raw.contains("multipart/alternative"), "{raw}");
        assert!(raw.contains("application/pdf"), "{raw}");
        assert!(raw.contains("filename=\"receipt-INV-1.pdf\""), "{raw}");
    }

    #[test]
    fn a_message_without_attachments_stays_a_plain_alternative() {
        let raw = formatted(&email(Vec::new()));
        assert!(raw.contains("multipart/alternative"), "{raw}");
        assert!(!raw.contains("multipart/mixed"), "{raw}");
    }

    #[test]
    fn smtp_config_requires_host_and_sender_identity() {
        let config = |host: Option<&str>, from_email: Option<&str>| {
            SmtpConfig::from_values(|key| match key {
                "SMTP_HOST" => host.map(str::to_owned),
                "SMTP_FROM_EMAIL" => from_email.map(str::to_owned),
                _ => None,
            })
        };

        assert!(config(None, Some("sender@example.com")).is_none());
        assert!(config(Some("smtp.example.com"), None).is_none());
        assert!(config(Some("   "), Some("sender@example.com")).is_none());

        let configured = config(Some("smtp.example.com"), Some("sender@example.com"))
            .expect("host and sender identity configure SMTP");
        assert_eq!(configured.host, "smtp.example.com");
        assert_eq!(configured.from_email, "sender@example.com");
        assert_eq!(configured.port, 587);
        assert_eq!(configured.security, "starttls");
    }
}
