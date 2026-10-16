package com.vayomitra.http;

import com.vayomitra.util.ApiException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Multipart {
    public record Part(String name, String filename, String contentType, byte[] data) {
        public String asText() {
            return new String(data, StandardCharsets.UTF_8).trim();
        }
    }

    private Multipart() {
    }

    public static Map<String, List<Part>> parse(String contentTypeHeader, byte[] body) {
        if (contentTypeHeader == null || !contentTypeHeader.toLowerCase().startsWith("multipart/form-data")) {
            throw new ApiException(415, "UNSUPPORTED_MEDIA_TYPE", "This endpoint requires multipart/form-data.");
        }
        String boundary = null;
        for (String piece : contentTypeHeader.split(";")) {
            String trimmed = piece.trim();
            if (trimmed.startsWith("boundary=")) {
                boundary = trimmed.substring("boundary=".length()).replace("\"", "");
            }
        }
        if (boundary == null || boundary.isBlank()) {
            throw ApiException.badRequest("Multipart boundary is missing.");
        }
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        List<int[]> parts = split(body, delimiter);
        Map<String, List<Part>> map = new LinkedHashMap<>();
        for (int[] range : parts) {
            int start = range[0];
            int end = range[1];
            if (end - start <= 4) {
                continue;
            }
            int headerEnd = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), start, end);
            if (headerEnd < 0) {
                continue;
            }
            String headers = new String(body, start, headerEnd - start, StandardCharsets.US_ASCII);
            int dataStart = headerEnd + 4;
            int dataEnd = end;
            if (dataEnd >= 2 && body[dataEnd - 2] == '\r' && body[dataEnd - 1] == '\n') {
                dataEnd -= 2;
            }
            String name = headerValue(headers, "name");
            if (name == null) {
                continue;
            }
            String filename = headerValue(headers, "filename");
            String partType = contentType(headers);
            byte[] data = new byte[Math.max(0, dataEnd - dataStart)];
            System.arraycopy(body, dataStart, data, 0, data.length);
            map.computeIfAbsent(name, key -> new ArrayList<>()).add(new Part(name, filename, partType, data));
        }
        return map;
    }

    private static List<int[]> split(byte[] body, byte[] delimiter) {
        List<int[]> ranges = new ArrayList<>();
        int index = indexOf(body, delimiter, 0, body.length);
        while (index >= 0) {
            int start = index + delimiter.length;
            if (start + 1 < body.length && body[start] == '\r' && body[start + 1] == '\n') {
                start += 2;
            }
            int next = indexOf(body, delimiter, start, body.length);
            if (next < 0) {
                break;
            }
            ranges.add(new int[]{start, next});
            index = next;
        }
        return ranges;
    }

    private static int indexOf(byte[] data, byte[] needle, int from, int to) {
        outer:
        for (int i = from; i <= to - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static String headerValue(String headers, String key) {
        for (String line : headers.split("\r\n")) {
            if (!line.toLowerCase().startsWith("content-disposition:")) {
                continue;
            }
            for (String piece : line.split(";")) {
                String trimmed = piece.trim();
                if (trimmed.startsWith(key + "=")) {
                    return trimmed.substring(key.length() + 1).replace("\"", "");
                }
            }
        }
        return null;
    }

    private static String contentType(String headers) {
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-type:")) {
                return line.substring("content-type:".length()).trim();
            }
        }
        return "application/octet-stream";
    }
}
