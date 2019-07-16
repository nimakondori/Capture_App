package murmur.partialscreenshots;

import android.databinding.BaseObservable;
import android.databinding.Bindable;


public class Results extends BaseObservable {
    String res1;
    GoalProgressBar prog1;

    // Making it Bindable will let the app update the UI every time it is updated
    @Bindable
    public String getRes1() {
        return res1;
    }

    public void setRes1(String res1) {
        this.res1 = "Predicted View: " + res1;
        notifyPropertyChanged(BR.res1);
    }
    @Bindable
    public GoalProgressBar getProg1() {
        return prog1;
    }

    public void setBar(GoalProgressBar bar) {
            this.prog1 = bar;
            notifyPropertyChanged(BR.prog1);
    }

}
