package tlb.Utils;

public class InfoIdentifier {
    private final Integer patchID;
    private final Double inputX;
    private final Double inputY;
    private final Double invadeProb;

    public InfoIdentifier(Integer patchID, Double inputX, Double inputY, Double invadeProb) {
        this.patchID = patchID;
        this.inputX = inputX; //longitude first
        this.inputY = inputY; //latitude
        this.invadeProb = invadeProb;
    }


    /*
     ***********************************************************************
     *                       Getters
     * *********************************************************************
     */

    public Integer getPatchID() { return patchID; }

    public double getInputX() { return inputX;}

    public double getInputY() {
        return inputY;
    }

    public Double getInvadeProb() { return invadeProb; }

}
