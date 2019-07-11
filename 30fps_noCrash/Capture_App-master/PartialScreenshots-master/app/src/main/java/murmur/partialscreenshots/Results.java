package murmur.partialscreenshots;

import android.databinding.BaseObservable;
import android.databinding.Bindable;

public class Results extends BaseObservable {
    String res1;
    int prog1;
    int tint1;
    // Making it Bindable will let the app update the UIevery time it is
    @Bindable
    public String getRes1() {
        return res1;
    }

    public void setRes1(String res1) {
        this.res1 = res1;
        notifyPropertyChanged(BR.res1);
    }
    @Bindable
    public int getProg1() {
        return prog1;
    }

    public void setProg1(int prog1) {
        this.prog1 = prog1;
        notifyPropertyChanged(BR.prog1);
    }
    @Bindable
    public int getTint1() {
        return tint1;
    }

    public void setTint1(int tint1) {
        this.tint1 = tint1;
        notifyPropertyChanged(BR.tint1);
    }
}
