package com.ruorain.ideas;

import android.content.Intent;
import android.os.Bundle;

/** 启动分发：没走过引导先去引导，走过了直接进首页；演示版直接进演示。 */
public class RouterActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean demoBuild = getResources().getBoolean(R.bool.is_demo_build);
        Intent i;
        if (demoBuild) {
            Store.setDemo(this, true);
            Store.setDone(this, true);
            i = new Intent(this, MainActivity.class);
        } else if (!Store.isDone(this)) {
            i = new Intent(this, OnboardingActivity.class);
        } else {
            i = new Intent(this, MainActivity.class);
        }
        startActivity(i);
        overridePendingTransition(R.anim.enter, R.anim.exit);
        finish();
    }
}
