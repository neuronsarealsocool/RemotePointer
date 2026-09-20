package systems.sieber.remotespotlight;

import android.os.Bundle;

public class HelpActivity extends BaseHelpActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FeatureCheck fc = new FeatureCheck(this);
        fc.setFeatureCheckReadyListener(new FeatureCheck.featureCheckReadyListener() {
            @Override
            public void featureCheckReady(boolean fetchSuccess) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        unlockPurchase("keyboard");
                        unlockPurchase("scanner");
                    }
                });
            }
        });
        fc.init();
    }

}
