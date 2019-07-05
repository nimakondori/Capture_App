package murmur.partialscreenshots;

import murmur.partialscreenshots.tflite.Classifier;

public class Results {
    String Res1;
    String Res2;
    String Res3;

    float Confidence1;
    float Confidence2;
    float Confidence3;

    public float getConfidence1() {
        return Confidence1;
    }

    public void setConfidence1(float confidence1) {
        Confidence1 = confidence1;
    }

    public float getConfidence2() {
        return Confidence2;
    }

    public void setConfidence2(float confidence2) {
        Confidence2 = confidence2;
    }

    public float getConfidence3() {
        return Confidence3;
    }

    public void setConfidence3(float confidence3) {
        Confidence3 = confidence3;
    }



    public String getRes1() {
        return Res1;
    }

    public String getRes2() {
        return Res2;
    }

    public String getRes3() {
        return Res3;
    }

    public void setRes1(String res1) {
        Res1 = res1;
    }

    public void setRes2(String res2) {
        Res2 = res2;
    }

    public void setRes3(String res3) {
        Res3 = res3;
    }
}
