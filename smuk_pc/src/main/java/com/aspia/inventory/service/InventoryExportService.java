package com.aspia.inventory.service;

import com.aspia.inventory.model.Host;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import com.aspia.inventory.model.ComponentChange;
import com.aspia.inventory.model.HostSoftware;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class InventoryExportService {

    private static final String[] HW_HEADERS = {
            "Имя ПК", "Системное имя", "Материнская плата", "Процессор",
            "ОЗУ", "Видеоадаптер", "ОС", "IP-адрес", "Статус"
    };

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    // ==================== EXCEL ====================

    public byte[] exportHardwareExcel(List<Host> hosts) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Оборудование");

            // Стили
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            // Заголовки
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HW_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HW_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Данные
            int rowNum = 1;
            for (Host host : hosts) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(host.getDisplayName());
                row.createCell(1).setCellValue(safe(host.getComputerName()));
                row.createCell(2).setCellValue(safe(host.getMotherboard()));
                row.createCell(3).setCellValue(safe(host.getCpuModel()));
                row.createCell(4).setCellValue(safe(host.getFormattedRam()));
                row.createCell(5).setCellValue(safe(host.getVideoAdapter()));
                row.createCell(6).setCellValue(safe(host.getOsName()));
                row.createCell(7).setCellValue(safe(host.getIpAddress()));
                row.createCell(8).setCellValue(host.getSyncError() != null ? "Ошибка" :
                        (host.isOnline() ? "Онлайн" : "Оффлайн"));
            }

            // Автоширина колонок
            for (int i = 0; i < HW_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportSoftwareExcel(List<Object[]> softwareList) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Программное обеспечение");

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            Row headerRow = sheet.createRow(0);
            String[] swHeaders = {"Программа", "Издатель", "Кол-во ПК"};
            for (int i = 0; i < swHeaders.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(swHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (Object[] sw : softwareList) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(sw[0] != null ? sw[0].toString() : "");
                row.createCell(1).setCellValue(sw[1] != null ? sw[1].toString() : "—");
                row.createCell(2).setCellValue(((Number) sw[2]).intValue());
            }

            for (int i = 0; i < swHeaders.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ==================== PDF ====================

    public byte[] exportHardwarePdf(List<Host> hosts) throws DocumentException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 20, 20, 30, 20);
        PdfWriter.getInstance(document, out);
        document.open();

        Font titleFont = getCyrillicFont(16, Font.BOLD);
        Font headerFont = getCyrillicFont(9, Font.BOLD);
        Font cellFont = getCyrillicFont(8, Font.NORMAL);
        Font metaFont = getCyrillicFont(8, Font.ITALIC);

        // Заголовок
        Paragraph title = new Paragraph("Инвентарь оборудования — SMUK PC Monitor", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(5);
        document.add(title);

        Paragraph meta = new Paragraph("Дата формирования: " + LocalDateTime.now().format(DT_FMT) +
                "  |  Всего устройств: " + hosts.size(), metaFont);
        meta.setAlignment(Element.ALIGN_CENTER);
        meta.setSpacingAfter(12);
        document.add(meta);

        // Таблица
        PdfPTable table = new PdfPTable(HW_HEADERS.length);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{12, 10, 14, 18, 6, 14, 12, 8, 6});

        for (String h : HW_HEADERS) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(new Color(52, 58, 64));
            cell.getPhrase().getFont().setColor(Color.WHITE);
            cell.setPadding(5);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        boolean alt = false;
        for (Host host : hosts) {
            Color bg = alt ? new Color(248, 249, 250) : Color.WHITE;
            addPdfCell(table, host.getDisplayName(), cellFont, bg);
            addPdfCell(table, safe(host.getComputerName()), cellFont, bg);
            addPdfCell(table, safe(host.getMotherboard()), cellFont, bg);
            addPdfCell(table, safe(host.getCpuModel()), cellFont, bg);
            addPdfCell(table, safe(host.getFormattedRam()), cellFont, bg);
            addPdfCell(table, safe(host.getVideoAdapter()), cellFont, bg);
            addPdfCell(table, safe(host.getOsName()), cellFont, bg);
            addPdfCell(table, safe(host.getIpAddress()), cellFont, bg);
            String status = host.getSyncError() != null ? "Ошибка" :
                    (host.isOnline() ? "Онлайн" : "Оффлайн");
            addPdfCell(table, status, cellFont, bg);
            alt = !alt;
        }

        document.add(table);
        document.close();
        return out.toByteArray();
    }

    public byte[] exportSoftwarePdf(List<Object[]> softwareList) throws DocumentException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 30, 30, 30, 20);
        PdfWriter.getInstance(document, out);
        document.open();

        Font titleFont = getCyrillicFont(16, Font.BOLD);
        Font headerFont = getCyrillicFont(10, Font.BOLD);
        Font cellFont = getCyrillicFont(9, Font.NORMAL);
        Font metaFont = getCyrillicFont(9, Font.ITALIC);

        Paragraph title = new Paragraph("Инвентарь ПО — SMUK PC Monitor", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(5);
        document.add(title);

        Paragraph meta = new Paragraph("Дата формирования: " + LocalDateTime.now().format(DT_FMT) +
                "  |  Уникальных программ: " + softwareList.size(), metaFont);
        meta.setAlignment(Element.ALIGN_CENTER);
        meta.setSpacingAfter(12);
        document.add(meta);

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{45, 35, 20});

        String[] swHeaders = {"Программа", "Издатель", "Кол-во ПК"};
        for (String h : swHeaders) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(new Color(52, 58, 64));
            cell.getPhrase().getFont().setColor(Color.WHITE);
            cell.setPadding(5);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        boolean alt = false;
        for (Object[] sw : softwareList) {
            Color bg = alt ? new Color(248, 249, 250) : Color.WHITE;
            addPdfCell(table, sw[0] != null ? sw[0].toString() : "", cellFont, bg);
            addPdfCell(table, sw[1] != null ? sw[1].toString() : "—", cellFont, bg);
            PdfPCell countCell = new PdfPCell(new Phrase(String.valueOf(((Number) sw[2]).intValue()), cellFont));
            countCell.setBackgroundColor(bg);
            countCell.setPadding(4);
            countCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(countCell);
            alt = !alt;
        }

        document.add(table);
        document.close();
        return out.toByteArray();
    }

    // ==================== Карточка хоста (PDF) ====================

    public byte[] exportHostCardPdf(Host host, List<HostSoftware> software, List<ComponentChange> changes)
            throws DocumentException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 30, 30, 30, 20);
        PdfWriter.getInstance(document, out);
        document.open();

        Font titleFont = getCyrillicFont(16, Font.BOLD);
        Font sectionFont = getCyrillicFont(13, Font.BOLD);
        Font labelFont = getCyrillicFont(9, Font.BOLD);
        Font valueFont = getCyrillicFont(9, Font.NORMAL);
        Font headerFont = getCyrillicFont(8, Font.BOLD);
        Font cellFont = getCyrillicFont(8, Font.NORMAL);
        Font metaFont = getCyrillicFont(8, Font.ITALIC);

        // === Заголовок ===
        Paragraph title = new Paragraph("Карточка устройства", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph hostName = new Paragraph(host.getDisplayName(), getCyrillicFont(14, Font.BOLD));
        hostName.setAlignment(Element.ALIGN_CENTER);
        hostName.setSpacingAfter(3);
        document.add(hostName);

        Paragraph meta = new Paragraph("Сформировано: " + LocalDateTime.now().format(DT_FMT) + "  |  SMUK PC Monitor", metaFont);
        meta.setAlignment(Element.ALIGN_CENTER);
        meta.setSpacingAfter(15);
        document.add(meta);

        // === Основная информация ===
        document.add(createSectionHeader("Конфигурация", sectionFont));

        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{30, 70});
        infoTable.setSpacingAfter(15);

        addInfoRow(infoTable, "Имя устройства", host.getDisplayName(), labelFont, valueFont);
        if (host.getAlias() != null && !host.getAlias().isEmpty()) {
            addInfoRow(infoTable, "Системное имя", host.getComputerName(), labelFont, valueFont);
        }
        addInfoRow(infoTable, "Host ID (Aspia)", String.valueOf(host.getAspiaHostId()), labelFont, valueFont);
        addInfoRow(infoTable, "IP-адрес", safe(host.getIpAddress()), labelFont, valueFont);
        addInfoRow(infoTable, "Операционная система", safe(host.getOsName()), labelFont, valueFont);
        addInfoRow(infoTable, "Архитектура", safe(host.getArchitecture()), labelFont, valueFont);
        addInfoRow(infoTable, "Материнская плата", safe(host.getMotherboard()), labelFont, valueFont);
        addInfoRow(infoTable, "Процессор", safe(host.getCpuModel()), labelFont, valueFont);
        addInfoRow(infoTable, "Оперативная память", host.getFormattedRam(), labelFont, valueFont);
        addInfoRow(infoTable, "Объём дисков", host.getFormattedDisk(), labelFont, valueFont);
        addInfoRow(infoTable, "Видеоадаптер", safe(host.getVideoAdapter()), labelFont, valueFont);
        addInfoRow(infoTable, "Версия агента", safe(host.getAspiaVersion()), labelFont, valueFont);
        String status = host.getSyncError() != null ? "Ошибка: " + host.getSyncError() :
                (host.isOnline() ? "Онлайн" : "Оффлайн");
        addInfoRow(infoTable, "Статус", status, labelFont, valueFont);
        addInfoRow(infoTable, "Последняя синхронизация",
                host.getLastSyncAt() != null ? host.getLastSyncAt().format(DT_FMT) : "—", labelFont, valueFont);
        addInfoRow(infoTable, "Группы",
                host.getGroupNamesString().isEmpty() ? "—" : host.getGroupNamesString(), labelFont, valueFont);

        document.add(infoTable);

        // === Программное обеспечение ===
        if (!software.isEmpty()) {
            document.add(createSectionHeader("Программное обеспечение (" + software.size() + ")", sectionFont));

            PdfPTable swTable = new PdfPTable(3);
            swTable.setWidthPercentage(100);
            swTable.setWidths(new float[]{50, 25, 25});
            swTable.setSpacingAfter(15);

            String[] swHeaders = {"Название", "Версия", "Издатель"};
            for (String h : swHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new Color(52, 58, 64));
                cell.getPhrase().getFont().setColor(Color.WHITE);
                cell.setPadding(4);
                swTable.addCell(cell);
            }

            boolean alt = false;
            for (HostSoftware sw : software) {
                Color bg = alt ? new Color(248, 249, 250) : Color.WHITE;
                addPdfCell(swTable, sw.getName(), cellFont, bg);
                addPdfCell(swTable, safe(sw.getVersion()), cellFont, bg);
                addPdfCell(swTable, safe(sw.getPublisher()), cellFont, bg);
                alt = !alt;
            }
            document.add(swTable);
        }

        // === История изменений ===
        if (!changes.isEmpty()) {
            document.add(createSectionHeader("История изменений (" + changes.size() + ")", sectionFont));

            PdfPTable chTable = new PdfPTable(5);
            chTable.setWidthPercentage(100);
            chTable.setWidths(new float[]{15, 15, 15, 27, 28});

            String[] chHeaders = {"Дата", "Компонент", "Тип", "Старое значение", "Новое значение"};
            for (String h : chHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new Color(52, 58, 64));
                cell.getPhrase().getFont().setColor(Color.WHITE);
                cell.setPadding(4);
                chTable.addCell(cell);
            }

            boolean alt = false;
            for (ComponentChange ch : changes) {
                Color bg = alt ? new Color(248, 249, 250) : Color.WHITE;
                addPdfCell(chTable, ch.getDetectedAt().format(DT_FMT), cellFont, bg);
                addPdfCell(chTable, ch.getComponentType(), cellFont, bg);
                String type = "ADDED".equals(ch.getChangeType()) ? "Добавлено" :
                        "REMOVED".equals(ch.getChangeType()) ? "Удалено" : "Изменено";
                addPdfCell(chTable, type, cellFont, bg);
                addPdfCell(chTable, safe(ch.getOldValue()), cellFont, bg);
                addPdfCell(chTable, safe(ch.getNewValue()), cellFont, bg);
                alt = !alt;
            }
            document.add(chTable);
        }

        document.close();
        return out.toByteArray();
    }

    // ==================== Карточка хоста (Excel) ====================

    public byte[] exportHostCardExcel(Host host, List<HostSoftware> software, List<ComponentChange> changes)
            throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle labelStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            labelStyle.setFont(boldFont);

            // --- Лист 1: Конфигурация ---
            Sheet configSheet = workbook.createSheet("Конфигурация");
            int r = 0;
            r = addExcelInfoRow(configSheet, r, "Имя устройства", host.getDisplayName(), labelStyle);
            if (host.getAlias() != null && !host.getAlias().isEmpty()) {
                r = addExcelInfoRow(configSheet, r, "Системное имя", host.getComputerName(), labelStyle);
            }
            r = addExcelInfoRow(configSheet, r, "Host ID (Aspia)", String.valueOf(host.getAspiaHostId()), labelStyle);
            r = addExcelInfoRow(configSheet, r, "IP-адрес", safe(host.getIpAddress()), labelStyle);
            r = addExcelInfoRow(configSheet, r, "Операционная система", safe(host.getOsName()), labelStyle);
            r = addExcelInfoRow(configSheet, r, "Архитектура", safe(host.getArchitecture()), labelStyle);
            r = addExcelInfoRow(configSheet, r, "Материнская плата", safe(host.getMotherboard()), labelStyle);
            r = addExcelInfoRow(configSheet, r, "Процессор", safe(host.getCpuModel()), labelStyle);
            r = addExcelInfoRow(configSheet, r, "Оперативная память", host.getFormattedRam(), labelStyle);
            r = addExcelInfoRow(configSheet, r, "Объём дисков", host.getFormattedDisk(), labelStyle);
            r = addExcelInfoRow(configSheet, r, "Видеоадаптер", safe(host.getVideoAdapter()), labelStyle);
            r = addExcelInfoRow(configSheet, r, "Версия агента", safe(host.getAspiaVersion()), labelStyle);
            String status = host.getSyncError() != null ? "Ошибка: " + host.getSyncError() :
                    (host.isOnline() ? "Онлайн" : "Оффлайн");
            r = addExcelInfoRow(configSheet, r, "Статус", status, labelStyle);
            r = addExcelInfoRow(configSheet, r, "Последняя синхронизация",
                    host.getLastSyncAt() != null ? host.getLastSyncAt().format(DT_FMT) : "—", labelStyle);
            addExcelInfoRow(configSheet, r, "Группы",
                    host.getGroupNamesString().isEmpty() ? "—" : host.getGroupNamesString(), labelStyle);
            configSheet.autoSizeColumn(0);
            configSheet.autoSizeColumn(1);

            // --- Лист 2: ПО ---
            Sheet swSheet = workbook.createSheet("Программное обеспечение");
            Row swHeader = swSheet.createRow(0);
            String[] swHeaders = {"Название", "Версия", "Издатель"};
            for (int i = 0; i < swHeaders.length; i++) {
                Cell cell = swHeader.createCell(i);
                cell.setCellValue(swHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            int swRow = 1;
            for (HostSoftware sw : software) {
                Row row = swSheet.createRow(swRow++);
                row.createCell(0).setCellValue(sw.getName());
                row.createCell(1).setCellValue(safe(sw.getVersion()));
                row.createCell(2).setCellValue(safe(sw.getPublisher()));
            }
            for (int i = 0; i < swHeaders.length; i++) swSheet.autoSizeColumn(i);

            // --- Лист 3: Изменения ---
            Sheet chSheet = workbook.createSheet("История изменений");
            Row chHeader = chSheet.createRow(0);
            String[] chHeaders = {"Дата", "Компонент", "Тип", "Старое значение", "Новое значение"};
            for (int i = 0; i < chHeaders.length; i++) {
                Cell cell = chHeader.createCell(i);
                cell.setCellValue(chHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            int chRow = 1;
            for (ComponentChange ch : changes) {
                Row row = chSheet.createRow(chRow++);
                row.createCell(0).setCellValue(ch.getDetectedAt().format(DT_FMT));
                row.createCell(1).setCellValue(ch.getComponentType());
                String type = "ADDED".equals(ch.getChangeType()) ? "Добавлено" :
                        "REMOVED".equals(ch.getChangeType()) ? "Удалено" : "Изменено";
                row.createCell(2).setCellValue(type);
                row.createCell(3).setCellValue(safe(ch.getOldValue()));
                row.createCell(4).setCellValue(safe(ch.getNewValue()));
            }
            for (int i = 0; i < chHeaders.length; i++) chSheet.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ==================== Helpers ====================

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private Paragraph createSectionHeader(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingBefore(8);
        p.setSpacingAfter(6);
        return p;
    }

    private void addInfoRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(com.lowagie.text.Rectangle.BOTTOM);
        labelCell.setBorderColor(new Color(222, 226, 230));
        labelCell.setPadding(5);
        labelCell.setBackgroundColor(new Color(248, 249, 250));
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "—", valueFont));
        valueCell.setBorder(com.lowagie.text.Rectangle.BOTTOM);
        valueCell.setBorderColor(new Color(222, 226, 230));
        valueCell.setPadding(5);
        table.addCell(valueCell);
    }

    private int addExcelInfoRow(Sheet sheet, int rowNum, String label, String value, CellStyle labelStyle) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(labelStyle);
        row.createCell(1).setCellValue(value != null ? value : "—");
        return rowNum + 1;
    }

    private Font getCyrillicFont(float size, int style) {
        // Пробуем найти системный шрифт с поддержкой кириллицы
        String[] candidates = {
                "/System/Library/Fonts/Supplemental/Arial.ttf",
                "/System/Library/Fonts/Helvetica.ttc",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
                "C:\\Windows\\Fonts\\arial.ttf"
        };
        for (String path : candidates) {
            if (new File(path).exists()) {
                try {
                    BaseFont bf = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    return new Font(bf, size, style);
                } catch (Exception ignored) {}
            }
        }
        // Fallback — встроенный шрифт (кириллица может не отображаться)
        return new Font(Font.HELVETICA, size, style);
    }

    private void addPdfCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "—", font));
        cell.setBackgroundColor(bg);
        cell.setPadding(4);
        table.addCell(cell);
    }

    private String safe(String value) {
        return value != null ? value : "—";
    }
}
