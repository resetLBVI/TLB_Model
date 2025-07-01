package tlb.Utils;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.coverage.grid.GridCoordinates2D;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geometry.Position2D;
import tlb.TLBEnvironment;

public class CoordinateConverter {
    public static int longitudeXtoGridX (double tlbLongX, double xllcorner, int cellSize) {
        int x = (int) (tlbLongX - xllcorner) / cellSize;
        return x;
    }

    /**
     * This method manually convert the latitude Y into Veg Cell Grid system
     * @param tlbLatY
     * @param yllcorner
     * @param cellSize
     * @param nRows
     * @return
     */
    public static int latitudeYtoGridY (double tlbLatY, double yllcorner, int cellSize, int nRows) {
        int y = (int) (yllcorner + cellSize * nRows - tlbLatY) / cellSize;
        return y;
    }

    public static int[] coordToGrid (CoordinateReferenceSystem crs, GridGeometry2D gg, double lon, double lat) throws TransformException {
        Position2D posWorld = new Position2D (crs, lon, lat); // longitude supplied first
        GridCoordinates2D posGrid = gg.worldToGrid(posWorld);
        int[] grids = new int[2];
        grids[0] = posGrid.x;
        grids[1] = posGrid.y;
        return grids;
    }

    public static double[] GridToCoord (GridGeometry2D gg, int x, int y) throws TransformException {
        ReferencedEnvelope pixelEnvelop = gg.gridToWorld(new GridEnvelope2D(x, y, 1, 1));
        double[] coords = new double[] {pixelEnvelop.getCenterX(), pixelEnvelop.getCenterY()};
        System.out.println(coords[0]); //longitude supplied first
        System.out.println(coords[1]);
        return coords;
    }

    public static int getVegToDisplayX (TLBEnvironment state, int vegGridX) {
        return vegGridX * state.agentGrid.getWidth() / state.vegetationRaster.getWidth();
    }

    public static int getVegToDisplayY (TLBEnvironment state, int vegGridY) {
        return vegGridY * state.agentGrid.getWidth() / state.vegetationRaster.getHeight();
    }
}
