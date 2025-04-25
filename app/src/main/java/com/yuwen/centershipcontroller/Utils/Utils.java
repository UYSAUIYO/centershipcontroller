package com.yuwen.centershipcontroller.Utils;

import android.content.Context;
import android.widget.Toast;

/**
 * @author yuwen404
 */
public class Utils {
    /**
     * Toast提示
     * @param msg 提示内容
     */
    public static void showMsg(Context context, String msg){
        Toast.makeText(context,msg,Toast.LENGTH_SHORT).show();
    }

}
