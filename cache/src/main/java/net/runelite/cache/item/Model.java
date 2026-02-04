/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.cache.item;

import net.runelite.cache.models.CircularAngle;

class Model extends Renderable
{
	boolean[] faceClipped = new boolean[6500];
	int[] modelViewportYs = new int[6500];
	int[] modelViewportXs = new int[6500];
	int[] modelViewportZs = new int[6500];
	int[] modelLocalX = new int[6500];
	int[] modelLocalY = new int[6500];
	int[] modelLocalZ = new int[6500];
	int[] distanceFaceCount = new int[1600];
	int[][] facesByDistance = new int[1600][512];
	int[] numOfPriority = new int[12];
	int[][] orderedFaces = new int[12][2000];
	int[] eq10 = new int[2000];
	int[] eq11 = new int[2000];
	int[] lt10 = new int[12];
	static int[] Model_sine;
	static int[] Model_cosine;
	int verticesCount;
	int[] verticesX;
	int[] verticesY;
	int[] verticesZ;
	int indicesCount;
	int[] indices1;
	int[] indices2;
	int[] indices3;
	int[] faceColors1;
	int[] faceColors2;
	int[] faceColors3;
	byte[] facePriorities;
	byte[] faceTransparencies;
	byte[] textureCoords;
	short[] faceTextures;
	int numTextureFaces;
	int[] texIndices1;
	int[] texIndices2;
	int[] texIndices3;
	int boundsType;
	int bottomY;
	int XYZMag;
	int diameter;
	int radius;
	public int extremeX;
	public int extremeY;
	public int extremeZ;

	static
	{
		Model_sine = CircularAngle.SINE;
		Model_cosine = CircularAngle.COSINE;
	}

	Model()
	{
		this.verticesCount = 0;
		this.indicesCount = 0;
		this.numTextureFaces = 0;
		this.extremeX = -1;
		this.extremeY = -1;
		this.extremeZ = -1;
	}

	public void calculateBoundsCylinder()
	{
		if (this.boundsType != 1)
		{
			this.boundsType = 1;
			super.modelHeight = 0;
			this.bottomY = 0;
			this.XYZMag = 0;

			for (int var1 = 0; var1 < this.verticesCount; ++var1)
			{
				int var2 = this.verticesX[var1];
				int var3 = this.verticesY[var1];
				int var4 = this.verticesZ[var1];
				if (-var3 > super.modelHeight)
				{
					super.modelHeight = -var3;
				}

				if (var3 > this.bottomY)
				{
					this.bottomY = var3;
				}

				int var5 = var2 * var2 + var4 * var4;
				if (var5 > this.XYZMag)
				{
					this.XYZMag = var5;
				}
			}

			this.XYZMag = (int) (Math.sqrt((double) this.XYZMag) + 0.99D);
			this.radius = (int) (Math.sqrt((double) (this.XYZMag * this.XYZMag + super.modelHeight * super.modelHeight)) + 0.99D);
			this.diameter = this.radius + (int) (Math.sqrt((double) (this.XYZMag * this.XYZMag + this.bottomY * this.bottomY)) + 0.99D);
		}
	}

	public final void projectAndDraw(Graphics3D graphics, int yzRotation, int xzRotation, int xyRotation, int orientation, int xOffset, int yOffset, int zOffset)
	{
		distanceFaceCount[0] = -1;
		// (re?)Calculate magnitude as necessary
		if (this.boundsType != 2 && this.boundsType != 1)
		{
			this.boundsType = 2;
			this.XYZMag = 0;

			for (int var1 = 0; var1 < this.verticesCount; ++var1)
			{
				int x = this.verticesX[var1];
				int y = this.verticesY[var1];
				int z = this.verticesZ[var1];
				int magnitude_squared = x * x + z * z + y * y;
				if (magnitude_squared > this.XYZMag)
				{
					this.XYZMag = magnitude_squared;
				}
			}

			this.XYZMag = (int)(Math.sqrt((double)this.XYZMag) + 0.99D);
			this.radius = this.XYZMag;
			this.diameter = this.XYZMag + this.XYZMag;
		}

		int centerX = graphics.centerX;
		int centerY = graphics.centerY;
		int sinR1 = Model_sine[yzRotation];
		int cosR1 = Model_cosine[yzRotation];
		int sinY = Model_sine[xzRotation];
		int cosY = Model_cosine[xzRotation];
		int sinZ = Model_sine[xyRotation];
		int cosZ = Model_cosine[xyRotation];
		int sinX = Model_sine[orientation];
		int cosX = Model_cosine[orientation];


		int zRelatedVariable = sinX * yOffset + cosX * zOffset >> 16;

		for (int i = 0; i < this.verticesCount; ++i)
		{
			int x = this.verticesX[i];
			int y = this.verticesY[i];
			int z = this.verticesZ[i];

			int tmp;
			if (xyRotation != 0)
			{
				tmp  = y * sinZ + x * cosZ >> 16;
				y = y * cosZ - x * sinZ >> 16;
				x = tmp;
			}

			if (yzRotation != 0)
			{
				tmp = y * cosR1 - z * sinR1 >> 16;
				z = y * sinR1 + z * cosR1 >> 16;
				y = tmp;
			}

			if (xzRotation != 0)
			{
				tmp  = z * sinY + x * cosY >> 16;
				z = z * cosY - x * sinY >> 16;
				x = tmp;
			}

			x += xOffset;
			y += yOffset;
			z += zOffset;
			tmp = y * cosX - z * sinX >> 16;
			z = y * sinX + z * cosX >> 16;
			modelViewportZs[i] = z - zRelatedVariable;
			modelViewportYs[i] = x * graphics.Rasterizer3D_zoom / z + centerX;
			modelViewportXs[i] = tmp * graphics.Rasterizer3D_zoom / z + centerY;
			if (faceTextures != null)
			{
				modelLocalX[i] = x;
				modelLocalY[i] = tmp;
				modelLocalZ[i] = z;
			}
		}

		this.draw(graphics);
	}

	private void draw(Graphics3D graphics)
	{
		if (this.diameter < 1600)
		{
			for (int var5 = 0; var5 < this.diameter; ++var5)
			{
				distanceFaceCount[var5] = 0;
			}

			int i;
			int idx1;
			int idx2;
			int idx3;
			int viewport1;
			int viewport2;
			int viewport3;

			int j;
			int var15;
			int l;

			for (i = 0; i < this.indicesCount; ++i)
			{
				if (this.faceColors3[i] != -2)
				{
					idx1 = this.indices1[i];
					idx2 = this.indices2[i];
					idx3 = this.indices3[i];

					viewport1 = modelViewportYs[idx1];
					viewport2 = modelViewportYs[idx2];
					viewport3 = modelViewportYs[idx3];

					int n;
					if ((viewport1 - viewport2) * (modelViewportXs[idx3] - modelViewportXs[idx2]) - (viewport3 - viewport2) * (modelViewportXs[idx1] - modelViewportXs[idx2]) > 0)
					{
						if (viewport1 >= 0 && viewport2 >= 0 && viewport3 >= 0 && viewport1 <= graphics.rasterClipX && viewport2 <= graphics.rasterClipX && viewport3 <= graphics.rasterClipX)
						{
							faceClipped[i] = false;
						}
						else
						{
							faceClipped[i] = true;
						}

						n = (modelViewportZs[idx1] + modelViewportZs[idx2] + modelViewportZs[idx3]) / 3 + this.radius;
						facesByDistance[n][distanceFaceCount[n]++] = i;
					}
				}
			}

			int[] faces;
			if (this.facePriorities == null) {
				for (i = this.diameter - 1; i >= 0; --i) {
					idx1 = distanceFaceCount[i];
					if (idx1 > 0)  {
						faces = facesByDistance[i];

						for (idx3 = 0; idx3 < idx1; ++idx3) {
							this.rasterFace(graphics, faces[idx3]);
						}
					}
				}

			} else  {
				for (i = 0; i < 12; ++i)  {
					numOfPriority[i] = 0;
					lt10[i] = 0;
				}

				for (i = this.diameter - 1; i >= 0; --i)  {
					idx1 = distanceFaceCount[i];
					if (idx1 > 0)  {
						faces = facesByDistance[i];

						for (idx3 = 0; idx3 < idx1; ++idx3)  {
							viewport1 = faces[idx3];
							byte priority = this.facePriorities[viewport1];
							viewport3 = numOfPriority[priority]++;
							orderedFaces[priority][viewport3] = viewport1;
							if (priority < 10)  {
								lt10[priority] += i;
							} else if (priority == 10)  {
								eq10[viewport3] = i;
							} else {
								eq11[viewport3] = i;
							}
						}
					}
				}

				i = 0;
				if (numOfPriority[1] > 0 || numOfPriority[2] > 0)
				{
					i = (lt10[1] + lt10[2]) / (numOfPriority[1] + numOfPriority[2]);
				}

				idx1 = 0;
				if (numOfPriority[3] > 0 || numOfPriority[4] > 0)
				{
					idx1 = (lt10[3] + lt10[4]) / (numOfPriority[3] + numOfPriority[4]);
				}

				idx2 = 0;
				if (numOfPriority[6] > 0 || numOfPriority[8] > 0)
				{
					idx2 = (lt10[8] + lt10[6]) / (numOfPriority[8] + numOfPriority[6]);
				}

				viewport1 = 0;
				viewport2 = numOfPriority[10];
				int[] ordFaces = orderedFaces[10];
				int[] eq = eq10;
				if (viewport1 == viewport2)
				{
					viewport1 = 0;
					viewport2 = numOfPriority[11];
					ordFaces = orderedFaces[11];
					eq = eq11;
				}

				if (viewport1 < viewport2)
				{
					idx3 = eq[viewport1];
				}
				else
				{
					idx3 = -1000;
				}

				for (j = 0; j < 10; ++j)
				{
					while (j == 0 && idx3 > i)
					{
						this.rasterFace(graphics, ordFaces[viewport1++]);
						if (viewport1 == viewport2 && ordFaces != orderedFaces[11])
						{
							viewport1 = 0;
							viewport2 = numOfPriority[11];
							ordFaces = orderedFaces[11];
							eq = eq11;
						}

						if (viewport1 < viewport2)
						{
							idx3 = eq[viewport1];
						}
						else
						{
							idx3 = -1000;
						}
					}

					while (j == 3 && idx3 > idx1)
					{
						this.rasterFace(graphics, ordFaces[viewport1++]);
						if (viewport1 == viewport2 && ordFaces != orderedFaces[11])
						{
							viewport1 = 0;
							viewport2 = numOfPriority[11];
							ordFaces = orderedFaces[11];
							eq = eq11;
						}

						if (viewport1 < viewport2)
						{
							idx3 = eq[viewport1];
						}
						else
						{
							idx3 = -1000;
						}
					}

					while (j == 5 && idx3 > idx2)
					{
						this.rasterFace(graphics, ordFaces[viewport1++]);
						if (viewport1 == viewport2 && ordFaces != orderedFaces[11])
						{
							viewport1 = 0;
							viewport2 = numOfPriority[11];
							ordFaces = orderedFaces[11];
							eq = eq11;
						}

						if (viewport1 < viewport2)
						{
							idx3 = eq[viewport1];
						}
						else
						{
							idx3 = -1000;
						}
					}

					var15 = numOfPriority[j];
					int[] var30 = orderedFaces[j];

					for (l = 0; l < var15; ++l)
					{
						this.rasterFace(graphics, var30[l]);
					}
				}

				while (idx3 != -1000)
				{
					this.rasterFace(graphics, ordFaces[viewport1++]);
					if (viewport1 == viewport2 && ordFaces != orderedFaces[11])
					{
						viewport1 = 0;
						ordFaces = orderedFaces[11];
						viewport2 = numOfPriority[11];
						eq = eq11;
					}

					if (viewport1 < viewport2)
					{
						idx3 = eq[viewport1];
					}
					else
					{
						idx3 = -1000;
					}
				}

			}
		}
	}

	private void rasterFace(Graphics3D graphics, int face)
	{
		int idx1 = this.indices1[face];
		int idx2 = this.indices2[face];
		int idx3 = this.indices3[face];
		graphics.rasterClipEnable = faceClipped[face];

		if (this.faceTransparencies == null) {
			graphics.rasterAlpha = 0;
		} else {
			graphics.rasterAlpha = this.faceTransparencies[face] & 255;
		}

		if (this.faceTextures != null && this.faceTextures[face] != -1)
		{
			int a;
			int b;
			int c;
			if (this.textureCoords != null && this.textureCoords[face] != -1)
			{
				int i = this.textureCoords[face] & 255;
				a = this.texIndices1[i];
				b = this.texIndices2[i];
				c = this.texIndices3[i];
			}
			else
			{
				a = idx1;
				b = idx2;
				c = idx3;
			}

			if (this.faceColors3[face] == -1)
			{
				graphics.rasterTextureAffine(modelViewportXs[idx1], modelViewportXs[idx2], modelViewportXs[idx3], modelViewportYs[idx1], modelViewportYs[idx2], modelViewportYs[idx3], this.faceColors1[face], this.faceColors1[face], this.faceColors1[face], modelLocalX[a], modelLocalX[b], modelLocalX[c], modelLocalY[a], modelLocalY[b], modelLocalY[c], modelLocalZ[a], modelLocalZ[b], modelLocalZ[c], this.faceTextures[face]);
			}
			else
			{
				graphics.rasterTextureAffine(modelViewportXs[idx1], modelViewportXs[idx2], modelViewportXs[idx3], modelViewportYs[idx1], modelViewportYs[idx2], modelViewportYs[idx3], this.faceColors1[face], this.faceColors2[face], this.faceColors3[face], modelLocalX[a], modelLocalX[b], modelLocalX[c], modelLocalY[a], modelLocalY[b], modelLocalY[c], modelLocalZ[a], modelLocalZ[b], modelLocalZ[c], this.faceTextures[face]);
			}
		}
		else if (this.faceColors3[face] == -1)
		{
			int palette = graphics.colorPalette[this.faceColors1[face]];
			graphics.rasterFlat(modelViewportXs[idx1], modelViewportXs[idx2], modelViewportXs[idx3], modelViewportYs[idx1], modelViewportYs[idx2], modelViewportYs[idx3], palette);
		}
		else
		{
			graphics.gouraudTriangle(modelViewportXs[idx1], modelViewportXs[idx2], modelViewportXs[idx3], modelViewportYs[idx1], modelViewportYs[idx2], modelViewportYs[idx3], this.faceColors1[face], this.faceColors2[face], this.faceColors3[face]);
		}
	}
}