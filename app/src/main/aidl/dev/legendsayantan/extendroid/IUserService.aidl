package dev.legendsayantan.extendroid;

import android.content.Intent;
import android.view.Display;

interface IUserService {

    void destroy() = 16777114;

    void launchIntentOnDisplay(Intent intent,Display display) = 1;
}