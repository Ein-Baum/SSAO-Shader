# SSAO-Shader
This repo was created to provide the files i used, to create the SSAO effect in my 3D Voxel Game from Youtube.
You can find a demonstration video <a href="https://www.youtube.com/watch?v=mnrTlqF9Tn0&lc=UgxHs49C2YfTaslMcSd4AaABAg.A2JGJNt0FmyA2XsnaJGeJT">here</a>!
I hope these files clear up some questions on how the shader and the stuff around it works. It took quite some time to get it to look right, so dont expect everything to work right away when you are implementing it yourself :).

## File explanation
You cannot just copy this and run it by yourself, as the java file uses some of my own wrappers that I used in my project (Also there is no main method or anything to set up the OpenGL stuff).
The SSAO shader files are ssao.fs and ssao.vs (.vs is the Vertex Shader and .fs is the Fragment Shader)
The two box blur shader stages use the same Vertex Shader but different Fragment Shader.
SSAORenderEffect.java is the file that would be used to initialize all FBOs and textures as well as the samples and so on.

## Without SSAO
<img src="WithoutSSAO.png" alt="Landscape without SSAO" style="float: left; margin-right: 10px;" />
With my awfull render engine I get around 400 FPS on average without the SSAO

## With SSAO
<img src="WithSSAO.png" alt="Landscape with SSAO" style="float: left; margin-right: 10px;" />
With SSAO turned on, I get 200 FPS
