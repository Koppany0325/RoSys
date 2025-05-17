package com.example.rosys.service;

import com.example.rosys.entities.Region;
import com.example.rosys.enums.PolygonCreationType;
import com.example.rosys.repositories.GridRepo;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


@Service
@RequiredArgsConstructor
public class GridServiceImpl implements GridService{

    private final GridRepo gridRepo;


    public File getRasterData(Integer firstBoundary, Integer secondBoundary, PolygonCreationType type) throws IOException {
        List<Map<String, Object>> rows = gridRepo.getGrids();
        Object raw = rows.get(0).get("values");

        int[][] raster = parseRasterValues(raw);
        int[][] classifiedMatrix = classifyMatrix(raster, firstBoundary, secondBoundary);

        Map<Integer, List<Polygon>> grouped = new HashMap<>();
        GeometryFactory geometryFactory = new GeometryFactory();
        Map<String, Object> map = gridRepo.getScaleAndOriginMap();
        double originX = ((Number) map.get("origin_x")).doubleValue();
        double originY = ((Number) map.get("origin_y")).doubleValue();
        double scaleX = ((Number) map.get("scale_x")).doubleValue();
        double scaleY = ((Number) map.get("scale_y")).doubleValue();



        List<Region> regions = findRegions(classifiedMatrix, raster);

        for (Region region : regions) {
            Polygon polygon = regionToPolygon(region, geometryFactory, originX, originY, scaleX, scaleY, type);
            grouped.computeIfAbsent(region.getValue(), k -> new ArrayList<>()).add(polygon);
        }

        Map<Integer, MultiPolygon> result = new HashMap<>();

        Map<Integer, Double> categoryAverages = new HashMap<>();


        for (Map.Entry<Integer, List<Polygon>> entry : grouped.entrySet()) {
            int category = entry.getKey();
            List<Polygon> polygons = entry.getValue();

            double total = 0;
            int count = 0;

            for (Region r : regions) {
                if (r.getValue() == category) {
                    total += r.getAvgOriginalValue();
                    count++;
                }
            }

            double avg = count == 0 ? 0 : total / count;
            categoryAverages.put(category, avg);

            MultiPolygon mp = geometryFactory.createMultiPolygon(polygons.toArray(new Polygon[0]));
            result.put(category, mp);
        }

        String geojson = toGeoJson(result, categoryAverages);

        File file = new File("output.geojson");

        try (PrintWriter out = new PrintWriter(file)) {
            out.print(geojson);
        }
        return file;
    }

    @Override
    public String getUrlIfExistsAndCreateNewIfNot(MultipartFile file) throws IOException {
        String geojson;
        if (file != null && !file.isEmpty()) {
            geojson = new String(file.getBytes(), StandardCharsets.UTF_8);
        } else {
            Path path = Paths.get("output.geojson");
            if(Files.exists(path)) {
                geojson = Files.readString(path);
            } else {
                getRasterData(null, null, null);
                geojson = Files.readString(path);
            }
        }
        String encoded = URLEncoder.encode(geojson, StandardCharsets.UTF_8)
                .replace("+", "%20");
        String geojsonIoUrl = "https://geojson.io/#data=data:application/json," + encoded;
        return geojsonIoUrl;
    }


    private  int[][] parseRasterValues(Object raw) {
        String rawStr = raw.toString().trim();
        int start = rawStr.indexOf("{{");
        int end = rawStr.lastIndexOf("}}") + 2;

        if (start == -1 || end == -1 || end <= start) {
            throw new IllegalArgumentException("Can't find raster array");
        }
        rawStr = rawStr.substring(start, end);
        String[] rowStrings = rawStr.substring(2, rawStr.length() - 2).split("\\},\\{");

        int rows = rowStrings.length;
        int cols = rowStrings[0].split(",").length;

        int[][] matrix = new int[rows][cols];

        for (int i = 0; i < rows; i++) {
            String[] values = rowStrings[i].split(",");
            for (int j = 0; j < cols; j++) {
                matrix[i][j] = Integer.parseInt(values[j].trim());
            }
        }

        return matrix;
    }

    private int[][] classifyMatrix(int[][] inputMatrix, Integer firstBoundary, Integer secondBoundary) {
        int rows = inputMatrix.length;
        int cols = inputMatrix[0].length;
        int[][] classified = new int[rows][cols];

        int lowerBoundary = firstBoundary == null ? 128 : firstBoundary;
        int upperBoundary = secondBoundary == null ? 137 : secondBoundary;

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                int value = inputMatrix[i][j];
                if (value <= lowerBoundary) {
                    classified[i][j] = 0;
                } else if (value <= upperBoundary) {
                    classified[i][j] = 1;
                } else {
                    classified[i][j] = 2;
                }
            }
        }

        return classified;
    }

    private List<Region> findRegions(int[][] classifiedMatrix, int[][] originalMatrix) {
        int rows = classifiedMatrix.length;
        int cols = classifiedMatrix[0].length;

        boolean[][] visited = new boolean[rows][cols];
        List<Region> regions = new ArrayList<>();

        int[][] directions = {
                {-1, -1}, {-1, 0}, {-1, 1},
                { 0, -1},          { 0, 1},
                { 1, -1}, { 1, 0}, { 1, 1}
        };
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (!visited[i][j]) {
                    int currentClass = classifiedMatrix[i][j];
                    Region region = new Region();
                    region.setValue(currentClass);

                    Queue<int[]> queue = new LinkedList<>();
                    queue.add(new int[]{i, j});
                    visited[i][j] = true;

                    int sumOriginal = 0;

                    while (!queue.isEmpty()) {
                        int[] point = queue.poll();
                        int x = point[0];
                        int y = point[1];
                        region.getPixels().add(point);
                        sumOriginal += originalMatrix[x][y];

                        for (int[] dir : directions) {
                            int nx = x + dir[0];
                            int ny = y + dir[1];

                            if (nx >= 0 && ny >= 0 && nx < rows && ny < cols) {
                                if (!visited[nx][ny] && classifiedMatrix[nx][ny] == currentClass) {
                                    queue.add(new int[]{nx, ny});
                                    visited[nx][ny] = true;
                                }
                            }
                        }
                    }

                    if (region.getPixels().size() >= 5000) {
                        region.setAvgOriginalValue(sumOriginal / (double) region.getPixels().size());
                        regions.add(region);
                    }
                }
            }
        }

        return regions;
    }

    public Polygon regionToPolygon(Region region, GeometryFactory geometryFactory, double originX, double originY, double scaleX, double scaleY, PolygonCreationType type) {

        if(type == null || type == PolygonCreationType.CONVEX_HULL) {
            Coordinate[] coords = region.getPixels().stream()
                    .map(p -> new Coordinate(
                            originX + p[1] * scaleX,
                            originY + p[0] * scaleY
                    ))
                    .toArray(Coordinate[]::new);
            Geometry geom = geometryFactory.createMultiPointFromCoords(coords).convexHull();

            if (geom instanceof Polygon) {
                return (Polygon) geom;
            } else {
                return geometryFactory.createPolygon();
            }
        } else {
            int[][] mask = generateMaskFromRegion(region, 512, 512);
            List<int[]> contour = traceContour(mask);

            Coordinate[] coords = contour.stream()
                    .map(p -> new Coordinate(originX + p[1] * scaleX, originY + p[0] * scaleY))
                    .toArray(Coordinate[]::new);
            coords = Arrays.copyOf(coords, coords.length + 1);
            coords[coords.length - 1] = coords[0];

            Polygon geom = geometryFactory.createPolygon(coords);

            return geom;
        }
    }

    private static int[][] generateMaskFromRegion(Region region, int rows, int cols) {
        int[][] mask = new int[rows][cols];

        for (int[] pixel : region.getPixels()) {
            int row = pixel[0];
            int col = pixel[1];
            mask[row][col] = 1;
        }

        return mask;
    }

    private static List<int[]> traceContour(int[][] binaryMask) {
        int rows = binaryMask.length;
        int cols = binaryMask[0].length;

        boolean[][] visited = new boolean[rows][cols];

        List<int[]> contour = new ArrayList<>();

        outerLoop:
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (binaryMask[i][j] == 1) {
                    contour.add(new int[]{i, j});
                    visited[i][j] = true;
                    followContour(i, j, binaryMask, visited, contour);
                    break outerLoop;
                }
            }
        }

        return contour;
    }

    private static String toGeoJson(Map<Integer, MultiPolygon> classifiedMultipolygons, Map<Integer, Double> categoryAverages) {
        StringBuilder sb = new StringBuilder();
        sb.append("{ \"type\": \"FeatureCollection\", \"features\": [");

        boolean firstFeature = true;
        for (Map.Entry<Integer, MultiPolygon> entry : classifiedMultipolygons.entrySet()) {
            int category = entry.getKey();
            MultiPolygon mp = entry.getValue();
            double avg = categoryAverages.getOrDefault(category, 0.0);

            if (!firstFeature) {
                sb.append(",");
            } else {
                firstFeature = false;
            }

            sb.append("{ \"type\": \"Feature\", \"properties\": { ");
            sb.append("\"category\": ").append(category).append(", ");
            sb.append("\"avg_value\": ").append(String.format(Locale.US, "%.2f", avg));
            sb.append(" }, ");
            sb.append("\"geometry\": ");
            sb.append(geometryToGeoJson(mp));
            sb.append("}");
        }

        sb.append("]}");
        return sb.toString();
    }

    private static String geometryToGeoJson(Geometry geometry) {
        if (geometry instanceof MultiPolygon) {
            MultiPolygon mp = (MultiPolygon) geometry;
            StringBuilder sb = new StringBuilder();
            sb.append("{ \"type\": \"MultiPolygon\", \"coordinates\": [");

            for (int i = 0; i < mp.getNumGeometries(); i++) {
                if (i > 0) sb.append(",");
                Polygon p = (Polygon) mp.getGeometryN(i);
                sb.append(polygonCoordinatesToGeoJson(p));
            }

            sb.append("] }");
            return sb.toString();
        } else if (geometry instanceof Polygon) {
            return "{ \"type\": \"Polygon\", \"coordinates\": " + polygonCoordinatesToGeoJson((Polygon) geometry) + "}";
        } else {
            throw new UnsupportedOperationException("Csak Polygon vagy MultiPolygon típus támogatott.");
        }
    }

    private static String polygonCoordinatesToGeoJson(Polygon polygon) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        sb.append(linearRingToJsonArray(polygon.getExteriorRing()));

        int holes = polygon.getNumInteriorRing();
        for (int i = 0; i < holes; i++) {
            sb.append(",");
            sb.append(linearRingToJsonArray(polygon.getInteriorRingN(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String linearRingToJsonArray(LineString ring) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        Coordinate[] coords = ring.getCoordinates();

        for (int i = 0; i < coords.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("[").append(coords[i].x).append(",").append(coords[i].y).append("]");
        }

        sb.append("]");
        return sb.toString();
    }



    private static void followContour(int startX, int startY, int[][] mask, boolean[][] visited, List<int[]> contour) {
        int rows = mask.length;
        int cols = mask[0].length;

        int x = startX;
        int y = startY;
        int dir = 0;

        while (true) {
            boolean foundNext = false;

            for (int i = 0; i < 8; i++) {
                int d = (dir + i) % 8;
                int nx = x + DIRECTIONS[d][0];
                int ny = y + DIRECTIONS[d][1];

                if (nx >= 0 && ny >= 0 && nx < rows && ny < cols) {
                    if (mask[nx][ny] == 1 && !visited[nx][ny]) {
                        contour.add(new int[]{nx, ny});
                        visited[nx][ny] = true;
                        x = nx;
                        y = ny;
                        dir = (d + 5) % 8;
                        foundNext = true;
                        break;
                    }
                }
            }

            if (!foundNext || (x == startX && y == startY)) {
                break;
            }
        }
    }

    private static final int[][] DIRECTIONS = {
            {-1,  0},
            {-1,  1},
            { 0,  1},
            { 1,  1},
            { 1,  0},
            { 1, -1},
            { 0, -1},
            {-1, -1}
    };
}
