#version 440 core

layout (std140, binding = 0) uniform Utils{
	mat4 projection; // The projection matrix of the scene. Should be the same as the one used on the normal geometry
	mat4 view;
	int time;
	vec3 camPos;
	float renderDistance;
};

layout (std140, binding = 2) uniform sampleBuffer{
	vec3 samples[64]; // The number of samples is hardcoded. Maybe this could be changed by using a shader storage buffer or a texture
};

const float bias = 0.04; // The bias is used to offset the hemisphere from the face so it doesnt clip inside

uniform float radius; // DEFAULT: 0.5
uniform sampler2D positions;
uniform sampler2D normals;
uniform sampler2D noise;

in vec2 uv;

out float out_color;

// For explainations of this shader see the following great resources:
// https://lettier.github.io/3d-game-shaders-for-beginners/ssao.html
// https://learnopengl.com/Advanced-Lighting/SSAO
// https://mtnphil.wordpress.com/2013/06/26/know-your-ssao-artifacts/
void main(){

	vec2 TextureScale = textureSize(positions, 0);
	int width = int(TextureScale.x);
	int height = int(TextureScale.y);
	
	vec4 positionRaw = texture(positions, uv);
	vec4 normalRaw = texture(normals, uv);
	
	// If there the pixel is not occluded by geometry, do not calculate SSAO on it.
	if(positionRaw.a!=0 && normalRaw.a!=0){

		vec3 position = positionRaw.xyz;
		vec3 normal = normalize(normalRaw.xyz);
		
		vec2 noiseTextureScale = textureSize(noise, 0);
		vec2 noiseScale = vec2( width/noiseTextureScale.x, height/noiseTextureScale.y);
		vec3 randomVec = normalize(texture(noise, uv * noiseScale).xyz);

		vec3 tangent = normalize(randomVec - normal * dot(randomVec, normal));
		
		vec3 bitangent = cross(normal, tangent);
		mat3 TBN = mat3(tangent, bitangent, normal);
		
		float occlusion = 0.0;
		
		for(int i = 0; i < samples.length(); i++){
			
			vec3 samplePos = TBN * samples[i];
			samplePos = position.xyz + samplePos * radius;
			
			vec4 offset = vec4(samplePos, 1.0);
			offset		= projection * offset;
			offset.xyz /= offset.w;
			offset.xyz = offset.xyz * 0.5 + 0.5;
				
			vec3 occluderPos = texture(positions, offset.xy).rgb;
				
			float rangecheck = smoothstep(0.0, 1.0, radius / abs(position.z - occluderPos.z));
				
			occlusion += (occluderPos.z >= samplePos.z + bias ? 1.0 : 0.0) * rangecheck;
			
		}
		occlusion = 1.0-(occlusion/samples.length());
		
		out_color = occlusion;
	
	}else{
	
		out_color = 1;
	
	}
}