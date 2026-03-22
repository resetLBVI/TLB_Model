package tlb.Utils;

public class VegAttributes {
    public final int patchID; //#1
    public final int terrID; //#2
    public final double pointX; //#3
    public final double pointY; //#4
    public double gisAcres; //#5
    public double pTamairsk; //#6
    public double terrTamariskDensity; //#7 territory Tamarisk density 0-3, initially the same as pTamarisk
    public boolean permanentlyDefoliated; //#8 determine if the Tamarisk in this territory is dead
    public int terrNumTlb; //#9 current number of TLB in the patch
    public double terrTotalTamariskFeed; //#10 the stress of Tamarisk of the territory
    public int terrNumDefoliation; //#11 determine how many times Tamarisk has been defoliated



    public VegAttributes(int patchID, int terrID, double pointX, double pointY, double gisAcres, double pTamairsk, double terrTamariskDensity, boolean permanentlyDefoliated) {
        this.patchID = patchID;
        this.terrID = terrID;
        this.pointX = pointX;
        this.pointY = pointY;
        this.gisAcres = gisAcres;
        this.pTamairsk = pTamairsk;
        this.terrTamariskDensity = terrTamariskDensity;
        this.permanentlyDefoliated = permanentlyDefoliated;
        // New fields default to 0
        this.terrNumTlb = 0;
        this.terrTotalTamariskFeed = 0.0;
        this.terrNumDefoliation = 0;


    }
}
