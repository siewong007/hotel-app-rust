//! One-page PDF copy of the checkout receipt, attached to the receipt email.
//!
//! Guest mail goes out in English, Malay and both Chinese scripts, so the
//! document embeds Noto Sans CJK SC (SIL OFL; licence beside the font in
//! `assets/fonts/`). krilla subsets the font to the glyphs each receipt uses,
//! so a receipt stays a few kilobytes although the font is ~19 MB in the
//! binary. Traditional Chinese renders with Simplified-region glyph forms.

use std::sync::{Arc, OnceLock};

use krilla::Document;
use krilla::color::rgb;
use krilla::geom::{PathBuilder, Point, Rect};
use krilla::page::PageSettings;
use krilla::paint::Fill;
use krilla::surface::Surface;
use krilla::text::{Font, TextDirection};

static FONT_BYTES: &[u8] = include_bytes!("../../../assets/fonts/NotoSansCJKsc-Regular.ttf");

/// Parsed once per process; immutable, so there is no cross-replica state.
fn font() -> Result<Font, String> {
    static FONT: OnceLock<Option<Font>> = OnceLock::new();
    FONT.get_or_init(|| Font::new(Arc::new(FONT_BYTES.to_vec()).into(), 0))
        .clone()
        .ok_or_else(|| "embedded receipt font could not be parsed".to_string())
}

/// What the PDF prints. Every string is already localized and formatted by
/// the caller, so the PDF and the email body share one source of figures.
#[derive(Debug, Clone)]
pub struct ReceiptPdf {
    pub hotel: String,
    pub heading: String,
    pub guest_name: String,
    pub rows: Vec<(String, String)>,
}

const PAGE_W: f32 = 595.0; // A4 in points
const PAGE_H: f32 = 842.0;
const MARGIN: f32 = 56.0;
const VALUE_X: f32 = 250.0;

fn color(surface: &mut Surface<'_>, r: u8, g: u8, b: u8) {
    surface.set_fill(Some(Fill {
        paint: rgb::Color::new(r, g, b).into(),
        ..Default::default()
    }));
}

fn rule(surface: &mut Surface<'_>, y: f32) {
    let mut path = PathBuilder::new();
    if let Some(rect) = Rect::from_xywh(MARGIN, y, PAGE_W - 2.0 * MARGIN, 0.75) {
        path.push_rect(rect);
    }
    if let Some(path) = path.finish() {
        color(surface, 200, 200, 200);
        surface.draw_path(&path);
    }
}

fn text(surface: &mut Surface<'_>, font: &Font, x: f32, y: f32, size: f32, value: &str) {
    surface.draw_text(
        Point::from_xy(x, y),
        font.clone(),
        size,
        value,
        false,
        TextDirection::Auto,
    );
}

/// Render the receipt. CPU-bound: call it from `spawn_blocking`.
pub fn render(receipt: &ReceiptPdf) -> Result<Vec<u8>, String> {
    let font = font()?;
    let mut document = Document::new();
    let settings = PageSettings::from_wh(PAGE_W, PAGE_H).ok_or("invalid page size")?;
    let mut page = document.start_page_with(settings);
    let mut surface = page.surface();

    let mut y = MARGIN + 18.0;
    color(&mut surface, 20, 20, 20);
    text(&mut surface, &font, MARGIN, y, 20.0, &receipt.hotel);
    y += 30.0;
    color(&mut surface, 60, 60, 60);
    text(&mut surface, &font, MARGIN, y, 14.0, &receipt.heading);
    y += 22.0;
    text(&mut surface, &font, MARGIN, y, 11.0, &receipt.guest_name);
    y += 16.0;
    rule(&mut surface, y);
    y += 26.0;

    for (label, value) in &receipt.rows {
        color(&mut surface, 100, 100, 100);
        text(&mut surface, &font, MARGIN, y, 11.0, label);
        color(&mut surface, 20, 20, 20);
        text(&mut surface, &font, VALUE_X, y, 11.0, value);
        y += 10.0;
        rule(&mut surface, y);
        y += 20.0;
    }

    surface.finish();
    page.finish();
    document
        .finish()
        .map_err(|e| format!("receipt PDF could not be written: {e:?}"))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample() -> ReceiptPdf {
        ReceiptPdf {
            hotel: "Salim Inn".to_string(),
            heading: "您的收据".to_string(),
            guest_name: "张伟 Zhang Wei".to_string(),
            rows: vec![
                ("预订".to_string(), "B-1004".to_string()),
                ("發票".to_string(), "INV-2026-000123".to_string()),
                ("Jumlah dibayar".to_string(), "1234.50".to_string()),
            ],
        }
    }

    #[test]
    fn renders_a_small_pdf_with_a_subset_cjk_font() {
        let pdf = render(&sample()).expect("receipt renders");
        assert!(pdf.starts_with(b"%PDF"), "not a PDF");
        // The full font is ~19 MB; anything near that means subsetting broke
        // and every receipt email would be rejected by mail servers.
        assert!(pdf.len() < 300 * 1024, "receipt PDF is {} bytes", pdf.len());
    }

    #[test]
    fn renders_with_no_rows() {
        let mut receipt = sample();
        receipt.rows.clear();
        assert!(render(&receipt).is_ok());
    }
}
