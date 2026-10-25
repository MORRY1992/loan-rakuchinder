package com.applylabpremium7.rakuchinder.loan;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(LoanMonetizationPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
