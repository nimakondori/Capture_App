package murmur.partialscreenshots;

import android.databinding.BaseObservable;
import android.databinding.Bindable;

public class Results extends BaseObservable {
    String res1;
    // Making it Bindable will let the app update the UIevery time it is
    @Bindable
    public String getRes1() {
        return res1;
    }

    public void setRes1(String res1) {
        this.res1 = res1;
        notifyPropertyChanged(BR.res1);
    }
}
