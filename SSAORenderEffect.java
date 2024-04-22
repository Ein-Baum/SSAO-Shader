package me.Mstudio.graphics.effects;

import java.nio.FloatBuffer;

import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;

import me.Mstudio.engine.memory.MemoryHandler;
import me.Mstudio.graphics.RenderEngine;
import me.Mstudio.graphics.IO.ScreenSizeListener;
import me.Mstudio.graphics.Models.Models;
import me.Mstudio.graphics.shaders.Shader;
import me.Mstudio.graphics.utils.EngineMath;
import me.Mstudio.graphics.utils.Texture;
import me.Mstudio.graphics.utils.UniformBuffer;

/**
 * This class file is from my 3D Voxel Game project which uses my own engine.
 * Therefore, it uses methods, wrappers and structures that are only present
 * in my game engine. Everything should be clear by looking at the comments though.
 * If not feel free to ask.
 * @author Ein_Baum
 */

public class SSAORenderEffect implements ScreenSizeListener{
	private static final int sampleSize = 64; 									// Hardcoded (also in the shader)
	
	private static Vector3f[] samples; 											// Holds every sample for every instance of SSAORenderEffect
	private static UniformBuffer sampleBuffer;									// A uniform buffer that holds the samples in the shader (could also be a texture or a shader storage buffer)
	private static Shader blurShaderV, blurShaderH, ssaoShader;					// All shaders that are used
	
	private int blurFBO, SSAOFBO, outputFBO;									// All FBOs that are used
	private int noiseTexture, outputTexture, blurTexture, SSAOOutputTexture;	// The color attachments of the FBOs
	private int blurLevel = 0;													// The size of the blur that is applied after the normal FBO
	private float hemisphereRadius = 0.5f;										// The radius of the hemisphere around the pixel that receives the SSAO effect in world space.
	
	public SSAORenderEffect(int blurLevel, float hemisphereRadius) {
		
		RenderEngine.getWindow().addScreenSizeListener(this); // Not important, this resizes the fbos if the window gets resized
		this.blurLevel = blurLevel;
		this.hemisphereRadius = hemisphereRadius;
		
		int width = RenderEngine.getWindow().getWidth(), height = RenderEngine.getWindow().getHeight();
		
		// Generate all OpenGL things that will be used later
		blurFBO = MemoryHandler.genFramebuffer();
		outputFBO = MemoryHandler.genFramebuffer();
		SSAOFBO = MemoryHandler.genFramebuffer();
		
		// Generate textures. For more information on what these values do, see documentation of "glTexImage2D"
		// Only the red channel needs to be used, as the ssao is a mask that makes pixels darker the more they are occluded.
		// RGB would therefore be overkill.
		//									      width  height  internalFormat format       dataType       filter           wrap
		blurTexture = Texture.createTexture(      width, height, GL40.GL_RED,   GL40.GL_RED, GL40.GL_FLOAT, GL40.GL_NEAREST, GL40.GL_CLAMP_TO_EDGE);
		
		outputTexture = Texture.createTexture(    width, height, GL40.GL_RED,   GL40.GL_RED, GL40.GL_FLOAT, GL40.GL_NEAREST, GL40.GL_CLAMP_TO_EDGE);
		
		SSAOOutputTexture = Texture.createTexture(width, height, GL40.GL_RED,   GL40.GL_RED, GL40.GL_FLOAT, GL40.GL_NEAREST, GL40.GL_CLAMP_TO_EDGE);
		
		// Attach the textures to the FBOs and tell them what to draw
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, SSAOFBO);
		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_TEXTURE_2D, SSAOOutputTexture, 0);
		GL30.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
		
		
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, blurFBO);
		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_TEXTURE_2D, blurTexture, 0);
		GL30.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
		
		
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, outputFBO);
		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_TEXTURE_2D, outputTexture, 0);
		GL30.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
		
		
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		
		// Generate a noise texture to tilt the hemisphere samples
		noiseTexture = generateNoiseTexture();
		
		if(sampleBuffer == null) { // This if statement ensures, that even if there are multiple SSAORenderEffect instances, all static variables only get initialized once.
			
			// Load the shaders (these are also in the repo under these names but not under these paths)
			// The first path is the vertex shader and the second path is the fragment shader.
			blurShaderH = new Shader("/shaders/voxel/ssao/blur", "/shaders/ppr/ssao/horizontal_blur", null);
			blurShaderV = new Shader("/shaders/voxel/ssao/blur", "/shaders/ppr/ssao/vertical_blur", null);
			ssaoShader = new Shader("/shaders/voxel/ssao/ssao");
			
			// Generate the random sample values in the hemisphere
			generateSSAOSamples();
			
			int[] sampleData = new int[sampleSize];
			
			for(int i = 0; i < sampleSize; i++)sampleData[i] = UniformBuffer.VECTOR3;
			
			// Allocate Memory for a uniform buffer that holds the hemisphere sample.
			// For documentation on how UniformBuffers work, look at https://learnopengl.com/Advanced-OpenGL/Advanced-GLSL
			//									  The size of the samples (16 * sampleSize)					   binding point of the uniform buffer (Hardcoded)
			sampleBuffer = UniformBuffer.allocate(MemoryHandler.getSizeOf(MemoryHandler.VECTOR3) * sampleSize, 2);
			sampleBuffer.resetBuffer();
			
			// Load everything into the buffer
			for(int i = 0; i < sampleSize; i++) {
				
				sampleBuffer.updateWith(samples[i]);
			}
			
		}
		
	}
	
	/**
	 * Uses this ssao effect on a certain scene.
	 * @param positions The scenes view space positions.
	 * @param normals The scenes view space normals.
	 * @return The texture id that holds the ssao mask.
	 */
	public int render(int positions, int normals) {
		
		//----------------------------------------------------------------------------------------------------------------- SSAO pass
		// This first pass will do the heavy lifting of the effect. It looks at every pixel and calculates based of a few sample vectors
		// in a hemisphere around it, which is rotated to match the direction of the geometry faces how much this pixel is occluded.
		
		GL30.glDisable(GL30.GL_DEPTH_TEST);
		GL40.glDisable(GL40.GL_BLEND);
		
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, SSAOFBO);
		GL40.glClearColor(0, 0, 0, 0);
		GL30.glClear(GL30.GL_COLOR_BUFFER_BIT);
		ssaoShader.use();
		
		GL30.glActiveTexture(GL30.GL_TEXTURE0);
		GL30.glBindTexture(GL30.GL_TEXTURE_2D, positions);
		ssaoShader.setInt("positions", 0);
		
		GL30.glActiveTexture(GL30.GL_TEXTURE1);
		GL30.glBindTexture(GL30.GL_TEXTURE_2D, normals);
		ssaoShader.setInt("normals", 1);
		
		GL30.glActiveTexture(GL30.GL_TEXTURE2);
		GL30.glBindTexture(GL30.GL_TEXTURE_2D, noiseTexture);
		ssaoShader.setInt("noise", 2);
		
		ssaoShader.setFloat("radius", hemisphereRadius);
		
		// This just means, that the whole screen will be filled by a single quad that holds the whole ssao mask later
		// The screen quad has the following geometry data:
		// VErtices: -1, -1, 0
		//			  1, -1, 0
		//           -1,  1, 0
		//            1,  1, 0
		// UV:		 0,0
		//			 1,0
		//			 0,1
		//			 1,1
		// Indices:  0,1,2,3,2,1
		
		Models.SCREEN_QUAD.bind();
		
		GL30.glDrawElements(GL30.GL_TRIANGLES, Models.SCREEN_QUAD.getIndices().length, GL30.GL_UNSIGNED_INT, 0);
		
		Models.SCREEN_QUAD.unbind();
		
		ssaoShader.stopUsing();
		
		// If no blur is required return raw unblurred ssao texture.
		// This would work as ssao, but it would be quite grainy, as the noise texture applies some noise
		// and the samples also do a bit of that. The mask should therefore be blurred a bit.
		if(blurLevel==0)return SSAOOutputTexture;
		
		// The following stuff is just a simple two pass box blur with no fancy things like gaussian kernels or something like that
		//----------------------------------------------------------------------------------------------------------------- FIRST BLUR PASS
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, blurFBO);
		GL30.glClearColor(0, 0, 0, 0);
		GL30.glClear(GL30.GL_COLOR_BUFFER_BIT);
		blurShaderH.use();
		
		GL30.glActiveTexture(GL30.GL_TEXTURE0);
		GL30.glBindTexture(GL30.GL_TEXTURE_2D, SSAOOutputTexture);
		blurShaderH.setInt("color", 0);
		
		blurShaderH.setFloat("size", blurLevel);
		
		Models.SCREEN_QUAD.bind();
		
		GL30.glDrawElements(GL30.GL_TRIANGLES, Models.SCREEN_QUAD.getIndices().length, GL30.GL_UNSIGNED_INT, 0);
		
		Models.SCREEN_QUAD.unbind();
		
		//----------------------------------------------------------------------------------------------------------------- SECOND BLUR PASS
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, outputFBO);
		GL30.glClearColor(0, 0, 0, 0);
		GL30.glClear(GL30.GL_COLOR_BUFFER_BIT);
		blurShaderV.use();
		
		GL30.glActiveTexture(GL30.GL_TEXTURE0);
		GL30.glBindTexture(GL30.GL_TEXTURE_2D, blurTexture);
		blurShaderV.setInt("color", 0);
		
		blurShaderV.setFloat("size", blurLevel);
		
		Models.SCREEN_QUAD.bind();
		
		GL30.glDrawElements(GL30.GL_TRIANGLES, Models.SCREEN_QUAD.getIndices().length, GL30.GL_UNSIGNED_INT, 0);

		Models.SCREEN_QUAD.unbind();
		
		blurShaderV.stopUsing();
		
		return outputTexture;
	}
	
	// Generates some samples that point into random directions in a hemisphere (halfsphere)
	// The vectors are in tangent space, meaning, that they are from the view of a single face, 
	// where the z value points in the direction of the face.
	private void generateSSAOSamples() {
		
		samples = new Vector3f[sampleSize];
		for(int i = 0; i < sampleSize; i++) {
		
			Vector3f sample = new Vector3f(
					
					EngineMath.getRandomizedFloatInRange(-1f, 1f),
					EngineMath.getRandomizedFloatInRange(-1f, 1f),
					EngineMath.getRandomizedFloatInRange(0, 1)
					
					);
			
			sample = sample.normalize();
			sample = sample.mul(EngineMath.getRandomizedFloatInRange(0, 1));
			
			float scale = (float)i / (float)sampleSize;
			sample = sample.mul(EngineMath.lerp(0.1f, 1, scale * scale));
			samples[i] = sample;
			
		}
		
	}
	
	// Generates a noise texture with random Red and Green values on every pixel
	// I guess, the blue values should not receive any noise as this might flip the hemisphere around.
	private int generateNoiseTexture() {
		
		int id = GL40.glGenTextures();
		MemoryHandler.addTexture(id);
		
		int size = Math.max(blurLevel,1)*2;
		FloatBuffer imageValues = BufferUtils.createFloatBuffer(size*size*3);
		
		for(int i = 0; i < size*size; i++) {
			imageValues.put(EngineMath.getRandomizedFloatInRange(-1, 1));
			imageValues.put(EngineMath.getRandomizedFloatInRange(-1, 1));
			imageValues.put(0);
		}
		
		imageValues.flip();
		
		GL40.glBindTexture(GL40.GL_TEXTURE_2D, id);
		GL40.glTexImage2D(GL40.GL_TEXTURE_2D, 0, GL40.GL_RGB32F, size, size, 0, GL40.GL_RGB, GL40.GL_FLOAT, imageValues);
		GL40.glTexParameteri(GL40.GL_TEXTURE_2D, GL40.GL_TEXTURE_MIN_FILTER, GL40.GL_NEAREST);
		GL40.glTexParameteri(GL40.GL_TEXTURE_2D, GL40.GL_TEXTURE_MAG_FILTER, GL40.GL_NEAREST);
		GL40.glTexParameteri(GL40.GL_TEXTURE_2D, GL40.GL_TEXTURE_WRAP_S, GL40.GL_REPEAT);
		GL40.glTexParameteri(GL40.GL_TEXTURE_2D, GL40.GL_TEXTURE_WRAP_T, GL40.GL_REPEAT);
		
		return id;
		
	}
	
	// Just deletes all things from OpenGL
	public void cleanUp() {
		GL30.glDeleteFramebuffers(SSAOFBO);
		GL30.glDeleteFramebuffers(blurFBO);
	}

	// Used if the Window gets resized (The ScreenSizeListener stuff)
	@Override
	public void changeScreenSize(int width, int height) {
		
		GL40.glBindTexture(GL40.GL_TEXTURE_2D, blurTexture);
		GL40.glTexImage2D(GL40.GL_TEXTURE_2D, 0, GL40.GL_RED, width, height, 0, GL40.GL_RED, GL40.GL_FLOAT, 0);
		GL40.glBindTexture(GL40.GL_TEXTURE_2D, outputTexture);
		GL40.glTexImage2D(GL40.GL_TEXTURE_2D, 0, GL40.GL_RED, width, height, 0, GL40.GL_RED, GL40.GL_FLOAT, 0);
		GL40.glBindTexture(GL40.GL_TEXTURE_2D, SSAOOutputTexture);
		GL40.glTexImage2D(GL40.GL_TEXTURE_2D, 0, GL40.GL_RED, width, height, 0, GL40.GL_RED, GL40.GL_FLOAT, 0);
		GL40.glBindTexture(GL40.GL_TEXTURE_2D, 0);
		
	}
	
	public int getNoiseTexture() {
		return noiseTexture;
	}
	
}
