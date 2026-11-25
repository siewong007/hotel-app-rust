package com.hotelapp.staff.core.print

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.hotelapp.staff.core.format.DateFormatter
import com.hotelapp.staff.core.format.MoneyFormatter
import com.hotelapp.staff.core.format.StatusFormatter
import com.hotelapp.staff.data.dto.InvoiceDto
import java.io.File
import java.io.FileOutputStream

object InvoicePdfPrinter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    fun printInvoice(
        context: Context,
        hotelName: String,
        invoice: InvoiceDto,
    ): Result<Unit> = runCatching {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: error("Android Print service is unavailable on this device.")
        val safeNumber = invoice.invoiceNumber.ifBlank { "Booking-${invoice.bookingId}" }
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?,
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("Invoice_$safeNumber.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?,
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                    return
                }
                val doc = buildPdfDocument(hotelName, invoice)
                try {
                    FileOutputStream(destination?.fileDescriptor).use { out ->
                        doc.writeTo(out)
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (t: Throwable) {
                    callback?.onWriteFailed(t.message ?: "Failed to render PDF")
                } finally {
                    doc.close()
                }
            }
        }
        manager.print("$hotelName Invoice $safeNumber", adapter, PrintAttributes.Builder().build())
    }

    fun shareInvoicePdf(
        context: Context,
        hotelName: String,
        invoice: InvoiceDto,
    ): Result<Unit> = runCatching {
        val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val safeName = invoice.invoiceNumber
            .ifBlank { "invoice-${invoice.bookingId}" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, "$safeName.pdf")
        val doc = buildPdfDocument(hotelName, invoice)
        try {
            FileOutputStream(file).use { out ->
                doc.writeTo(out)
            }
        } finally {
            doc.close()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "$hotelName Invoice ${invoice.invoiceNumber}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Invoice PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun buildPdfDocument(
        hotelName: String,
        invoice: InvoiceDto,
    ): PdfDocument {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(17, 24, 39)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val headingPaint = Paint().apply {
            color = Color.rgb(17, 24, 39)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(55, 65, 81)
            textSize = 11.5f
            isAntiAlias = true
        }
        val mutedPaint = Paint().apply {
            color = Color.rgb(107, 114, 128)
            textSize = 10.5f
            isAntiAlias = true
        }
        val valueRightPaint = Paint(bodyPaint).apply {
            textAlign = Paint.Align.RIGHT
        }
        val totalRightPaint = Paint(headingPaint).apply {
            textAlign = Paint.Align.RIGHT
        }
        val dividerPaint = Paint().apply {
            color = Color.rgb(229, 231, 235)
            strokeWidth = 1f
        }

        val left = 48f
        val right = PAGE_WIDTH - 48f
        var y = 64f

        canvas.drawText(hotelName, left, y, titlePaint)
        y += 22f
        canvas.drawText("TAX INVOICE / FOLIO STATEMENT", left, y, headingPaint)
        y += 18f
        canvas.drawLine(left, y, right, y, dividerPaint)
        y += 26f

        fun row(label: String, value: String, bold: Boolean = false) {
            val lPaint = if (bold) headingPaint else bodyPaint
            val rPaint = if (bold) totalRightPaint else valueRightPaint
            canvas.drawText(label, left, y, lPaint)
            canvas.drawText(value, right, y, rPaint)
            y += 20f
        }

        row("Invoice Number", invoice.invoiceNumber.ifBlank { "Draft Preview" }, bold = true)
        row("Status", StatusFormatter.humanize(invoice.status))
        row("Issue Date", DateFormatter.formatDate(invoice.issueDate))
        invoice.dueDate?.let { row("Due Date", DateFormatter.formatDate(it)) }

        y += 10f
        canvas.drawLine(left, y, right, y, dividerPaint)
        y += 24f
        canvas.drawText("BILL TO", left, y, mutedPaint)
        y += 18f
        canvas.drawText(invoice.billingName.ifBlank { "Guest" }, left, y, headingPaint)
        y += 18f
        invoice.billingEmail?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText(it, left, y, bodyPaint)
            y += 18f
        }
        invoice.billingAddress?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText(it.take(80), left, y, bodyPaint)
            y += 18f
        }

        y += 10f
        canvas.drawLine(left, y, right, y, dividerPaint)
        y += 24f
        canvas.drawText("STAY DETAILS", left, y, mutedPaint)
        y += 18f
        val roomDesc = listOfNotNull(
            invoice.roomNumber?.let { "Room $it" },
            invoice.roomType,
        ).joinToString(" · ").ifBlank { "Reservation #${invoice.bookingId}" }
        row("Accommodation", roomDesc)
        val stayDates = DateFormatter.formatDateRange(invoice.checkInDate, invoice.checkOutDate)
        if (stayDates.isNotBlank()) row("Stay Window", stayDates)
        invoice.numberOfNights?.let { row("Nights", it.toString()) }

        y += 10f
        canvas.drawLine(left, y, right, y, dividerPaint)
        y += 24f
        canvas.drawText("CHARGES & PAYMENTS", left, y, mutedPaint)
        y += 20f

        val cur = invoice.currency.ifBlank { "MYR" }
        row("Subtotal", MoneyFormatter.format(invoice.subtotal, cur))
        row("Taxes & Fees", MoneyFormatter.format(invoice.taxAmount, cur))
        if (invoice.discountAmount > java.math.BigDecimal.ZERO) {
            row("Discounts", "-${MoneyFormatter.format(invoice.discountAmount, cur)}")
        }
        y += 4f
        canvas.drawLine(left, y, right, y, dividerPaint)
        y += 22f
        row("Total Amount", MoneyFormatter.format(invoice.totalAmount, cur), bold = true)
        row("Amount Paid", MoneyFormatter.format(invoice.paidAmount, cur))
        row("Balance Due", MoneyFormatter.format(invoice.balanceDue, cur), bold = true)

        invoice.notes?.takeIf { it.isNotBlank() }?.let { note ->
            y += 16f
            canvas.drawText("Notes: ${note.take(100)}", left, y, mutedPaint)
        }

        doc.finishPage(page)
        return doc
    }
}
