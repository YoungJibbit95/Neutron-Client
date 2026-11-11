#ifndef NEUTRON_CONTOUR_COORDINATE
#define NEUTRON_CONTOUR_COORDINATE
// Original Neutron arc-length implementation, matching PerimeterOrbit.contour.
float uiContour(vec2 point, vec2 size, float radius) {
 vec2 wh=max(size,vec2(1.0));
 vec2 p=clamp(point,vec2(0.0),wh);
 float r=clamp(radius,0.0,min(wh.x,wh.y)*.5);
 float top=wh.x-2.0*r, right=wh.y-2.0*r, arc=1.57079632679*r;
 float perimeter=2.0*top+2.0*right+4.0*arc;
 float distance;
 if(r>0.0&&p.x>=wh.x-r&&p.y<=r){
  distance=top+r*(atan(p.y-r,p.x-(wh.x-r))+1.57079632679);
 }else if(r>0.0&&p.x>=wh.x-r&&p.y>=wh.y-r){
  distance=top+arc+right+r*atan(p.y-(wh.y-r),p.x-(wh.x-r));
 }else if(r>0.0&&p.x<=r&&p.y>=wh.y-r){
  distance=2.0*top+right+2.0*arc+r*(atan(p.y-(wh.y-r),p.x-r)-1.57079632679);
 }else if(r>0.0&&p.x<=r&&p.y<=r){
  float angle=atan(p.y-r,p.x-r);if(angle<0.0)angle+=6.28318530718;
  distance=2.0*top+2.0*right+3.0*arc+r*(angle-3.14159265359);
 }else{
  float nearest=min(min(p.y,wh.x-p.x),min(wh.y-p.y,p.x));
  if(nearest==p.y)distance=clamp(p.x-r,0.0,top);
  else if(nearest==wh.x-p.x)distance=top+arc+clamp(p.y-r,0.0,right);
  else if(nearest==wh.y-p.y)distance=top+right+2.0*arc+clamp(wh.x-r-p.x,0.0,top);
  else distance=2.0*top+right+3.0*arc+clamp(wh.y-r-p.y,0.0,right);
 }
 return fract(distance/max(1.0,perimeter));
}
#endif
