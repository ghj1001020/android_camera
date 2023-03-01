package com.ghj.camera.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.ghj.camera.common.Code;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.dialog.MaterialDialogs;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class Util {

    // 권한체크
    public static int checkPermission(Activity activity, String[] permissions) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return Code.PERMISSION.PERMISSION_GRANTED;
        }

        if (permissions == null || permissions.length == 0) {
            return Code.PERMISSION.PERMISSION_GRANTED;
        }

        List<String> deniedPermissions = new ArrayList<>();
        boolean isRationale = false;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(activity, perm) != PackageManager.PERMISSION_GRANTED) {
                deniedPermissions.add(perm);

                // 이전에 거부한적 있는지
                if(ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)) {
                    isRationale = true;
                }
            }
        }

        if(deniedPermissions.size() > 0 && !isRationale) {
            return Code.PERMISSION.PERMISSION_DENIED;
        }
        else if(deniedPermissions.size() > 0 && isRationale) {
            return Code.PERMISSION.PERMISSION_RATIONALE;
        }
        else {
            return Code.PERMISSION.PERMISSION_GRANTED;
        }
    }

    // 오늘날짜
    public static String getToday(String format) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"), Locale.KOREA);
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(calendar.getTime());
    }

    // alert
    public static AlertDialog alert(Context context, String title, String message, DialogInterface.OnClickListener listener) {
        return alert(context, title, message, "", listener);
    }

    public static AlertDialog alert(Context context, String title, String message, String text, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        if(!TextUtils.isEmpty(title)) {
            builder.setTitle(title);
        }
        builder.setCancelable(false);
        builder.setMessage(message);
        if(TextUtils.isEmpty(text)) {
            text = "확인";
        }
        builder.setPositiveButton(text, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if(listener != null) {
                    listener.onClick(dialog, which);
                }
                dialog.dismiss();
            }
        });
        return builder.show();
    }

    // confirm
    public static void confirm(Context context, String message, DialogInterface.OnClickListener positiveListener, DialogInterface.OnClickListener negativeListener) {
        confirm(context, "", message, "확인", "취소", positiveListener, negativeListener);
    }

    // confirm
    public static void confirm(Context context, String title, String message, String positive, String negative, DialogInterface.OnClickListener positiveListener, DialogInterface.OnClickListener negativeListener) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        if(!TextUtils.isEmpty(title))
            builder.setTitle(title);
        builder.setCancelable(false);
        builder.setMessage(message);
        builder.setPositiveButton(positive, positiveListener);
        builder.setNegativeButton(negative, negativeListener);
        builder.show();
    }

    // 권한 설정 화면으로 이동
    public static void goToPermissionSetting(Context context) {
        Intent intent = null;
        try
        {
            intent = new Intent( Settings.ACTION_APPLICATION_DETAILS_SETTINGS ).setData( Uri.parse( "package:" + context.getPackageName() ) );
        }
        catch ( ActivityNotFoundException e ) {
            intent = new Intent( Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS );
        }

        context.startActivity(intent);
    }
}
