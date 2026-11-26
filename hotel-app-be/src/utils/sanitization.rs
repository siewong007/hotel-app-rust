use ammonia::clean;

/// Input sanitization utilities to prevent XSS and injection attacks
pub struct Sanitizer;

impl Sanitizer {
    /// Sanitize HTML content, removing dangerous tags and attributes
    ///
    /// Uses ammonia library which implements a safe HTML whitelist approach.
    /// Removes script tags, event handlers, and other potentially dangerous HTML.
    ///
    /// # Arguments
    /// * `input` - Raw HTML input that may contain malicious content
    ///
    /// # Returns
    /// * Safe HTML string with dangerous elements removed
    pub fn sanitize_html(input: &str) -> String {
        clean(input)
    }

    /// Sanitize plain text by removing control characters
    ///
    /// Keeps only printable characters and common whitespace (space, newline, tab, carriage return).
    /// Prevents control character injection attacks.
    ///
    /// # Arguments
    /// * `input` - Raw text input
    ///
    /// # Returns
    /// * Sanitized text with control characters removed
    pub fn sanitize_text(input: &str) -> String {
        input
            .chars()
            .filter(|c| !c.is_control() || *c == '\n' || *c == '\r' || *c == '\t' || *c == ' ')
            .collect()
    }

    /// Sanitize and normalize email addresses
    ///
    /// Trims whitespace and converts to lowercase for consistent storage.
    ///
    /// # Arguments
    /// * `input` - Raw email input
    ///
    /// # Returns
    /// * Normalized email address
    pub fn sanitize_email(input: &str) -> String {
        input.trim().to_lowercase()
    }

    /// Sanitize phone numbers by removing non-digit characters except + prefix
    ///
    /// # Arguments
    /// * `input` - Raw phone number input
    ///
    /// # Returns
    /// * Phone number with only digits and optional + prefix
    pub fn sanitize_phone(input: &str) -> String {
        let trimmed = input.trim();
        let mut result = String::new();

        for (i, c) in trimmed.chars().enumerate() {
            if c.is_ascii_digit() || (i == 0 && c == '+') {
                result.push(c);
            }
            // Skip all other characters (spaces, dashes, parentheses, etc.)
        }

        result
    }

    /// Sanitize a single-line email/HTTP header value by stripping all control
    /// characters (including `\r`, `\n`, `\t`) without HTML-entity encoding.
    pub fn sanitize_header_line(input: &str) -> String {
        input
            .chars()
            .filter(|c| !c.is_control())
            .collect::<String>()
            .trim()
            .to_string()
    }

    /// Escape PostgreSQL LIKE/ILIKE pattern metacharacters (`\`, `%`, `_`) and
    /// cap pattern length so untrusted search terms cannot trigger pathological
    /// wildcard matching. Pairs with SQL `ESCAPE '\'`.
    pub fn escape_like_pattern(input: &str) -> String {
        let mut out = String::with_capacity(input.len().min(200));
        for c in input.chars().take(200) {
            match c {
                '\\' | '%' | '_' => {
                    out.push('\\');
                    out.push(c);
                }
                _ => out.push(c),
            }
        }
        out
    }

    /// Sanitize URL by validating scheme and removing dangerous protocols
    ///
    /// Only allows http, https, and mailto schemes.
    ///
    /// # Arguments
    /// * `input` - Raw URL input
    ///
    /// # Returns
    /// * Option<String> - Some(url) if valid, None if dangerous
    pub fn sanitize_url(input: &str) -> Option<String> {
        Self::sanitize_url_with_mailto(input, true)
    }

    /// Sanitize an HTTP/HTTPS URL (rejecting `mailto:`, `javascript:`, `data:`,
    /// control characters, whitespace, and malformed URLs).
    pub fn sanitize_http_url(input: &str) -> Option<String> {
        Self::sanitize_url_with_mailto(input, false)
    }

    fn sanitize_url_with_mailto(input: &str, allow_mailto: bool) -> Option<String> {
        if input.chars().any(|c| c.is_control()) {
            return None;
        }
        let trimmed = input.trim();
        let lower = trimmed.to_ascii_lowercase();

        if trimmed.is_empty()
            || trimmed
                .chars()
                .any(|c| c.is_whitespace() || matches!(c, '<' | '>' | '"' | '\'' | '\\'))
        {
            return None;
        }

        let candidate = if lower.starts_with("http://") || lower.starts_with("https://") {
            trimmed.to_string()
        } else if allow_mailto && lower.starts_with("mailto:") {
            let addr = &trimmed["mailto:".len()..];
            if addr.is_empty() || !addr.contains('@') {
                return None;
            }
            return Some(trimmed.to_string());
        } else if lower.starts_with("//") || trimmed.contains(':') {
            // Reject protocol-relative //host, javascript:, data:, etc.
            return None;
        } else {
            format!("https://{}", trimmed)
        };

        let parsed = reqwest::Url::parse(&candidate).ok()?;
        if !matches!(parsed.scheme(), "http" | "https") || parsed.host_str().is_none() {
            return None;
        }
        Some(candidate)
    }

    /// Comprehensive sanitization for guest input
    ///
    /// Applies appropriate sanitization based on field type.
    pub fn sanitize_guest_name(name: &str) -> String {
        // Remove control characters but allow international characters
        Self::sanitize_text(name).trim().to_string()
    }

    /// Sanitize booking notes/remarks
    ///
    /// Allows newlines and common punctuation but removes dangerous HTML/scripts.
    pub fn sanitize_notes(notes: &str) -> String {
        // First remove any HTML
        let html_free = Self::sanitize_html(notes);
        // Then remove control characters except newlines/tabs
        Self::sanitize_text(&html_free)
    }
}

/// Escape a single value for CSV output and neutralise spreadsheet formula
/// injection. Quote-wrapping alone still executes `=HYPERLINK(...)` in Excel;
/// prefixing a leading `= + - @ | \t \r \n` (or whitespace followed by one)
/// with a single quote forces the cell to be treated as text. Every CSV writer
/// in the codebase should funnel values through this helper so callers inherit
/// the guard.
pub fn csv_cell(value: &str) -> String {
    let escaped = value.replace('"', "\"\"");
    // OWASP CSV-injection mitigation: a leading formula trigger (= + - @ | TAB
    // CR LF, or leading spaces followed by a formula trigger) is prefixed with a
    // single quote so spreadsheet apps treat the cell as text instead of
    // evaluating it.
    let leading_trigger = matches!(
        escaped.chars().next(),
        Some('=' | '+' | '-' | '@' | '|' | '\t' | '\r' | '\n')
    ) || matches!(
        escaped
            .trim_start_matches([' ', '\t', '\r', '\n'])
            .chars()
            .next(),
        Some('=' | '+' | '-' | '@' | '|')
    );
    let guarded = if leading_trigger {
        format!("'{escaped}")
    } else {
        escaped
    };
    format!("\"{guarded}\"")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sanitize_html() {
        let malicious = "<script>alert('XSS')</script><p>Safe content</p>";
        let sanitized = Sanitizer::sanitize_html(malicious);
        assert!(!sanitized.contains("<script"));
        assert!(sanitized.contains("Safe content"));
    }

    #[test]
    fn test_sanitize_html_removes_event_handlers_and_dangerous_links() {
        let malicious = r#"<a href="javascript:alert(1)" onclick="steal()">Profile</a>"#;
        let sanitized = Sanitizer::sanitize_html(malicious);

        assert!(!sanitized.contains("javascript:"));
        assert!(!sanitized.contains("onclick"));
        assert!(sanitized.contains("Profile"));
    }

    #[test]
    fn test_sanitize_text() {
        let input = "Hello\x00World\x1FTest";
        let sanitized = Sanitizer::sanitize_text(input);
        assert_eq!(sanitized, "HelloWorldTest");
    }

    #[test]
    fn test_sanitize_text_preserves_allowed_whitespace() {
        let input = "Line 1\nLine 2\tTabbed\rReturn";
        let sanitized = Sanitizer::sanitize_text(input);
        assert_eq!(sanitized, input);
    }

    #[test]
    fn test_sanitize_email() {
        assert_eq!(
            Sanitizer::sanitize_email("  Test@Example.COM  "),
            "test@example.com"
        );
    }

    #[test]
    fn test_sanitize_phone() {
        assert_eq!(
            Sanitizer::sanitize_phone("+1 (415) 555-2671"),
            "+14155552671"
        );
        assert_eq!(Sanitizer::sanitize_phone("415-555-2671"), "4155552671");
    }

    #[test]
    fn test_sanitize_url() {
        assert_eq!(
            Sanitizer::sanitize_url("https://example.com"),
            Some("https://example.com".to_string())
        );
        assert_eq!(
            Sanitizer::sanitize_url("HTTP://example.com"),
            Some("HTTP://example.com".to_string())
        );
        assert_eq!(Sanitizer::sanitize_url("javascript:alert('XSS')"), None);
        assert_eq!(
            Sanitizer::sanitize_url("data:text/html,<script>alert(1)</script>"),
            None
        );
        assert_eq!(Sanitizer::sanitize_url("//example.com/path"), None);
        assert_eq!(Sanitizer::sanitize_url("   "), None);
        assert_eq!(
            Sanitizer::sanitize_url("example.com"),
            Some("https://example.com".to_string())
        );
        assert_eq!(Sanitizer::sanitize_url("https://example.com\r\n"), None);
        assert_eq!(Sanitizer::sanitize_http_url("mailto:a@example.com"), None);
        assert_eq!(
            Sanitizer::sanitize_http_url("https://cdn.example.com/a.png"),
            Some("https://cdn.example.com/a.png".to_string())
        );
    }

    #[test]
    fn test_sanitize_header_line_and_like_escape_and_csv_cell() {
        assert_eq!(
            Sanitizer::sanitize_header_line("  Bed & Breakfast\r\nBcc: evil@x  "),
            "Bed & BreakfastBcc: evil@x"
        );
        assert_eq!(
            Sanitizer::escape_like_pattern(r"100%_test\path"),
            r"100\%\_test\\path"
        );
        assert_eq!(csv_cell("=CMD()"), "\"'=CMD()\"");
        assert_eq!(csv_cell("  =CMD()"), "\"'  =CMD()\"");
        assert_eq!(csv_cell("\n=CMD()"), "\"'\n=CMD()\"");
        assert_eq!(csv_cell("|calc"), "\"'|calc\"");
        assert_eq!(csv_cell("plain text"), "\"plain text\"");
    }
}
