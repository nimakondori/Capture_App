package com.segmentation.bubbletestapp;

import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;


public class BubbleHandler {
    private final BubbleService service;
    private int initialX;
    private int initialY;
    private float initialTouchX;
    private float initialTouchY;
    private float moveDistance;


    BubbleHandler(BubbleService service) {
        this.service = service;
    }

    public WindowManager.LayoutParams pushToEdge (WindowManager.LayoutParams params)
    {
            params.x = 0;
            params.y = 0;
            return params;
    }

    public boolean onTouch(View view, MotionEvent motionEvent) {
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) view.getLayoutParams();
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                moveDistance = 0;
                initialX = params.x;
                initialY = params.y;
                initialTouchX = motionEvent.getRawX();
                initialTouchY = motionEvent.getRawY();
                break;
            case MotionEvent.ACTION_UP:
                view.performClick();
                BubbleService.stop = !BubbleService.stop;
                if (Float.compare(moveDistance, 100f) >= 0) {
                    service.checkInCloseRegion(motionEvent.getRawX(), motionEvent.getRawY());
                    if(service.checkInClipRegion(motionEvent.getRawX(), motionEvent.getRawY(), BubbleService.clipRegionService)) {
                        params = pushToEdge(params);
                        service.updateViewLayout(view, params);
                        service.removeTrashLayout();
                    }
                } else {
                    service.startClipMode();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                params.x = initialX + (int) (motionEvent.getRawX() - initialTouchX);
                params.y = initialY + (int) (motionEvent.getRawY() - initialTouchY);
                float distance = motionEvent.getRawX() - initialTouchX
                        + motionEvent.getRawY() - initialTouchY;
                moveDistance += Math.abs(distance);
                service.updateViewLayout(view, params);
                break;
        }
        return true;
    }
}

