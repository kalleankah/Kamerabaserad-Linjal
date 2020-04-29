package com.example.cameraxopengl;

class vec2 {
    float x;
    float y;

    vec2(float _x, float _y){
        x = _x;
        y = _y;
    }

    vec2(float[] xy){
        x = xy[0];
        y = xy[1];
    }

    void normalizeVec(){
        float length = (float) Math.sqrt( x*x + y*y );
        x = x/length;
        y = y/length;
    }

    // vec1 + vec2
    public void addVec2(vec2 vec){
        x = x+vec.x;
        y = y+vec.y;
    }

    // this - vec2
    public void subVec2(vec2 vec){
        x = x-vec.x;
        y = y-vec.y;
    }

    // vec1 + vec2
    static vec2 addVec2(vec2 vec1, vec2 vec2){
        return new vec2(vec1.x+vec2.x, vec1.y+vec2.y);
    }

    // vec1 - vec2
    static vec2 subVec2(vec2 vec1, vec2 vec2){
        return new vec2(vec1.x-vec2.x, vec1.y-vec2.y);
    }

    // vec1 - vec2
    public static vec2 subVec2(float[] vec1, float[] vec2){
        return new vec2(vec1[0]-vec2[0], vec1[1]-vec2[1]);
    }

    void scale(float v) {
        x = x*v;
        y = y*v;
    }
}
