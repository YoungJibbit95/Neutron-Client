package me.youngjibbit.utils.render;

public class AnimationEngine {

    private long prevTime;
    private float animationValue;
    private float startValue;
    private float endValue;
    private boolean isIncreasing;
    private float changeValuePms;
    private boolean isDrawAnimation = false;
    private boolean resetUsingBackWardsAnimation = false;

    public AnimationEngine(float startValue, float endValue, long time) {
        this(startValue, endValue, time, false);
    }

    public AnimationEngine(float startValue, float endValue, long time, boolean instaDrawAnimation) {
        this.prevTime = System.currentTimeMillis();
        this.startValue = startValue;
        this.endValue = (startValue == endValue ? endValue + 1 : endValue);
        this.animationValue = startValue;
        this.isIncreasing = endValue > startValue;
        float animationDistance = Math.abs(startValue - endValue);
        this.changeValuePms = animationDistance / time;
        this.isDrawAnimation = instaDrawAnimation;
    }

    public AnimationEngine() {}

    public float getAnimationValue() {
        updateAnimationValue(); // 👈 this is crucial
        return animationValue;
    }

    private void updateAnimationValue() {
        long currentTime = System.currentTimeMillis();
        long delta = currentTime - prevTime;

        if (isDrawAnimation) {
            if (animationValue == endValue) return;

            float deltaValue = changeValuePms * delta;

            if (isIncreasing) {
                animationValue += deltaValue;
                if (animationValue >= endValue) {
                    animationValue = endValue;
                    isDrawAnimation = false;
                }
            } else {
                animationValue -= deltaValue;
                if (animationValue <= endValue) {
                    animationValue = endValue;
                    isDrawAnimation = false;
                }
            }
        } else if (resetUsingBackWardsAnimation) {
            float deltaValue = changeValuePms * delta;

            if (isIncreasing) {
                animationValue -= deltaValue;
                if (animationValue <= startValue) {
                    animationValue = startValue;
                    resetUsingBackWardsAnimation = false;
                }
            } else {
                animationValue += deltaValue;
                if (animationValue >= startValue) {
                    animationValue = startValue;
                    resetUsingBackWardsAnimation = false;
                }
            }
        }

        prevTime = currentTime;
    }

    public boolean isAnimationDone() {
        return animationValue == endValue;
    }

    public void reset() {
        animationValue = startValue;
        prevTime = System.currentTimeMillis();
    }

    public void AnimationUpdateValue(float startValue, float endValue, long time) {
        AnimationUpdateValue(startValue, endValue, time, false);
    }

    public void AnimationUpdateValue(float startValue, float endValue, long time, boolean instaDrawAnimation) {
        this.prevTime = System.currentTimeMillis();
        this.startValue = startValue;
        this.endValue = (startValue == endValue ? endValue + 1 : endValue);
        this.animationValue = startValue;
        this.isIncreasing = endValue > startValue;
        float animationDistance = Math.abs(startValue - endValue);
        this.changeValuePms = animationDistance / time;
        this.isDrawAnimation = instaDrawAnimation;
    }

    public void setIsDrawAnimation(boolean drawAnimation) {
        this.isDrawAnimation = drawAnimation;
    }

    public boolean getIsDrawAnimation() {
        return isDrawAnimation;
    }

    public void resetUsingBackWardsAnimation() {
        prevTime = System.currentTimeMillis();
        setIsDrawAnimation(false);
        this.resetUsingBackWardsAnimation = true;
    }
}
