package org.dalol.pulltoloadlayout;

import android.view.View;

/**
 * @author Filippo Engidashet <filippo.eng@gmail.com>
 * @version 1.0.0
 * @since Tuesday, 06/03/2018 at 13:21.
 */
public final class ViewUtils {

    private ViewUtils(){}

    public static boolean canChildScrollUp(View view) {
        return view != null && view.canScrollVertically(-1);
    }

    public static boolean canChildScrollDown(View view) {
        return view != null && view.canScrollVertically(1);
    }
}
