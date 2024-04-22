#version 440 core
layout (location = 0) in vec3 positions;
layout (location = 1) in vec2 uvs;

out vec2 uv;

void main(){

	uv = uvs;
	gl_Position = vec4(positions,1);

}