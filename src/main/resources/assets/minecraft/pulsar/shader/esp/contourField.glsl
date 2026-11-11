// RGBA8 coordinate packing. Zero is empty; valid coordinates reserve integer zero.
vec2 fieldPack16(float value) {
    float n=floor(clamp(value,0.0,1.0)*65534.0+1.5);
    return vec2(floor(n/256.0),mod(n,256.0))/255.0;
}
vec4 fieldPack(vec2 value) {return vec4(fieldPack16(value.x),fieldPack16(value.y));}
vec2 fieldUnpack(vec4 value) {
    vec4 n=floor(value*255.0+.5);
    return (vec2(n.x*256.0+n.y,n.z*256.0+n.w)-1.0)/65534.0;
}
bool fieldValid(vec4 value) {return dot(value,vec4(1.0))>.0001;}
