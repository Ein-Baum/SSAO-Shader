#version 440 core

uniform sampler2D color;
uniform float size;

in vec2 uv;

out float out_color;

void main(){

	out_color = 0;//texture2D(color, uv).r;
	
	float blurColor = 0;
	vec2 texSize = vec2(textureSize(color,0));
	vec2 texelSize = 1.0 / texSize;
	
	for(float y = -size; y < size; ++y){
				
				vec2 offset = uv + vec2(0,float(y)) * texelSize;
				
				float r = texture2D(color, offset).r;
				
				blurColor += r;
				
	}
	
	out_color=blurColor/(size*2);
}