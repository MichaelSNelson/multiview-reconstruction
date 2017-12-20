/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.mvrecon.headless.deconvolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxTools;
import net.preibisch.mvrecon.process.deconvolution.DeconView;
import net.preibisch.mvrecon.process.deconvolution.DeconViewPSF.PSFTYPE;
import net.preibisch.mvrecon.process.deconvolution.DeconViews;
import net.preibisch.mvrecon.process.deconvolution.MultiViewDeconvolution;
import net.preibisch.mvrecon.process.deconvolution.iteration.ComputeBlockThreadCPUFactory;
import net.preibisch.mvrecon.process.deconvolution.iteration.ComputeBlockThreadFactory;
import net.preibisch.mvrecon.process.deconvolution.iteration.PsiInitialization;
import net.preibisch.mvrecon.process.deconvolution.iteration.PsiInitialization.PsiInit;
import net.preibisch.mvrecon.process.deconvolution.iteration.mul.ComputeBlockMulThreadCPUFactory;
import net.preibisch.mvrecon.process.deconvolution.iteration.mul.ComputeBlockMulThreadFactory;
import net.preibisch.mvrecon.process.deconvolution.iteration.mul.MultiViewDeconvolutionMul;
import net.preibisch.mvrecon.process.deconvolution.iteration.PsiInitializationAvgApprox;
import net.preibisch.mvrecon.process.deconvolution.iteration.PsiInitializationAvgPrecise;
import net.preibisch.mvrecon.process.deconvolution.iteration.PsiInitializationBlurredFused;
import net.preibisch.mvrecon.process.deconvolution.util.PSFPreparation;
import net.preibisch.mvrecon.process.deconvolution.util.ProcessInputImages;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.transformed.FusedRandomAccessibleInterval;
import net.preibisch.mvrecon.process.fusion.transformed.weightcombination.CombineWeightsRandomAccessibleInterval;
import net.preibisch.mvrecon.process.fusion.transformed.weightcombination.CombineWeightsRandomAccessibleInterval.CombineType;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.psf.PSFCombination;

public class TestDeconvolutionMul
{
	public static void main( String[] args ) throws SpimDataException
	{
		new ImageJ();

		// load drosophila
		SpimData2 spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/BIMSB/Projects/Betzig/testDataforStephen_2017_12_14/cell/dataset.xml" );
		Collection< Group< ViewDescription > >groups = Group.toGroups( spimData.getSequenceDescription().getViewDescriptions().values() );

		BoundingBox bb = BoundingBoxTools.maximalBoundingBox( spimData, new ArrayList<>( spimData.getSequenceDescription().getViewDescriptions().values() ), "All Views" );

		testDeconvolution( spimData, groups, bb, true );
	}

	public static < V extends ViewId > void testDeconvolution(
			final SpimData2 spimData,
			final Collection< Group< V > > deconVirtualViews,
			final BoundingBox boundingBox,
			final boolean mul )
	{
		if ( boundingBox == null )
		{
			System.out.println( "Bounding box null." );
			return;
		}

		IOFunctions.println( BoundingBox.getBoundingBoxDescription( boundingBox ) );

		for ( final Group< V > virtualView : deconVirtualViews )
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): " + Group.gvids( Group.getViewsSorted( virtualView.getViews() ) ) );

		final double osemSpeedUp = 1.0;
		final double downsampling = 2.0;

		final ProcessInputImages< V > fusion = new ProcessInputImages<>(
				spimData,
				deconVirtualViews,
				boundingBox,
				downsampling,
				true,
				FusionTools.defaultBlendingRange,
				FusionTools.defaultBlendingBorder,
				true,
				MultiViewDeconvolution.defaultBlendingRange,
				MultiViewDeconvolution.defaultBlendingBorder / ( Double.isNaN( downsampling ) ? 1.0f : (float)downsampling ) );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Virtual Fusion of 'virtual views' " );
		fusion.fuseGroups();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Normalizing weights ... " );
		fusion.normalizeWeights( osemSpeedUp, false, MultiViewDeconvolution.maxDiffRange, MultiViewDeconvolution.scalingRange );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): De-virtualization ... " );
		fusion.cacheImages();
		fusion.cacheNormalizedWeights();
		fusion.cacheUnnormalizedWeights();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Displaying " );
		//displayDebug( fusion );
		//SimpleMultiThreading.threadHaltUnClean();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Loading, grouping, and transforming PSF's " );

		final HashMap< Group< V >, ArrayImg< FloatType, ? > > psfs =
				PSFPreparation.loadGroupTransformPSFs( spimData.getPointSpreadFunctions(), fusion );

		//final Img< FloatType > avgPSF = PSFCombination.computeAverageImage( psfs.values(), new ArrayImgFactory< FloatType >(), true );
		//DisplayImage.getImagePlusInstance( Views.rotate( avgPSF, 0, 2 ), false, "avgPSF", 0, 1 ).show();

		final Img< FloatType > maxAvgPSF = PSFCombination.computeMaxAverageTransformedPSF( psfs.values(), new ArrayImgFactory< FloatType >() );
		DisplayImage.getImagePlusInstance( maxAvgPSF, false, "maxAvgPSF", 0, 1 ).show();

		final ImgFactory< FloatType > blockFactory = new ArrayImgFactory<>();
		final ImgFactory< FloatType > psiFactory = new ArrayImgFactory<>();
		final int[] blockSize = new int[]{ 256, 256, 256 };
		final int numIterations = 10;
		final float lambda = 0.0006f;
		final PSFTYPE psfType = PSFTYPE.INDEPENDENT;
		final boolean filterBlocksForContent = false;
		final PsiInit psiInitType = PsiInit.FUSED_BLURRED;
		final boolean debug = true;
		final int debugInterval = 1;

		// one common ExecutorService for all
		final ExecutorService service = DeconViews.createExecutorService();

		try
		{
			final ComputeBlockThreadFactory cptf;
			final ComputeBlockMulThreadFactory cpmtf;

			if ( mul )
			{
				cptf = null;
				cpmtf = new ComputeBlockMulThreadCPUFactory(
						service,
						deconVirtualViews.size(),
						lambda,
						blockSize,
						blockFactory );
			}
			else
			{
				cpmtf = null;
				cptf = new ComputeBlockThreadCPUFactory(
						service,
						lambda,
						blockSize,
						blockFactory );
			}
			
			final PsiInitialization psiInit;

			if ( psiInitType == PsiInit.FUSED_BLURRED )
				psiInit = new PsiInitializationBlurredFused();
			else if ( psiInitType == PsiInit.AVG )
				psiInit = new PsiInitializationAvgPrecise();
			else
				psiInit = new PsiInitializationAvgApprox();

			if ( filterBlocksForContent )
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting up blocks for deconvolution and testing for empty ones that can be dropped." );
			else
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting up blocks for deconvolution." );

			final ArrayList< DeconView > deconViews = new ArrayList<>();

			for ( final Group< V > group : fusion.getGroups() )
			{
				final DeconView view = new DeconView(
						service,
						fusion.getImages().get( group ),
						fusion.getNormalizedWeights().get( group ),
						psfs.get( group ),
						psfType,
						blockSize,
						mul ? cpmtf.numParallelBlocks() : cptf.numParallelBlocks(),
						filterBlocksForContent );

				if ( view.getNumBlocks() <= 0 )
					return;

				view.setTitle( Group.gvids( group ) );
				deconViews.add( view );

				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Added " + view );
			}

			final DeconViews views = new DeconViews( deconViews, service );
			final Img< FloatType > psi;

			if ( mul )
			{
				final MultiViewDeconvolutionMul decon = new MultiViewDeconvolutionMul( views, numIterations, psiInit, cpmtf, psiFactory );
				if ( !decon.initWasSuccessful() )
					return;
				decon.setDebug( debug );
				decon.setDebugInterval( debugInterval );
				decon.runIterations();
				psi = decon.getPSI();
			}
			else
			{
				final MultiViewDeconvolution decon = new MultiViewDeconvolution( views, numIterations, psiInit, cptf, psiFactory );
				if ( !decon.initWasSuccessful() )
					return;
				decon.setDebug( debug );
				decon.setDebugInterval( debugInterval );
				decon.runIterations();
				psi = decon.getPSI();
			}

			ImagePlus imp = DisplayImage.getImagePlusInstance( psi, false, "Deconvolved", Double.NaN, Double.NaN );
			imp.getCalibration().xOrigin = -(boundingBox.min( 0 ) / downsampling);
			imp.getCalibration().yOrigin = -(boundingBox.min( 1 ) / downsampling);
			imp.getCalibration().zOrigin = -(boundingBox.min( 2 ) / downsampling);
			imp.getCalibration().pixelWidth = imp.getCalibration().pixelHeight = imp.getCalibration().pixelDepth = downsampling;
			imp.show();

			service.shutdown();
		}
		catch ( OutOfMemoryError oome )
		{
			IJ.log( "Out of Memory" );
			IJ.error("Multi-View Deconvolution", "Out of memory.  Check \"Edit > Options > Memory & Threads\"");

			service.shutdown();

			return;
		}
	}

	public static < V extends ViewId > void displayDebug( final ProcessInputImages< V > fusion )
	{
		int i = 0;

		final ArrayList< RandomAccessibleInterval< FloatType > > allWeightsNormed = new ArrayList<>();

		for ( final Group< V > group : fusion.getGroups() )
		{
			System.out.println( "Img Instance: " + fusion.getImages().get( group ).getClass().getSimpleName() );
			System.out.println( "Raw Weight Instance: " + fusion.getUnnormalizedWeights().get( group ).getClass().getSimpleName() );
			System.out.println( "Normalized Weight Instance: " + fusion.getNormalizedWeights().get( group ).getClass().getSimpleName() );

			DisplayImage.getImagePlusInstance( fusion.getImages().get( group ), true, "g=" + i + " image", 0, 255 ).show();
			DisplayImage.getImagePlusInstance( fusion.getUnnormalizedWeights().get( group ), true, "g=" + i + " weightsRawDecon", 0, 2 ).show();
			DisplayImage.getImagePlusInstance( fusion.getNormalizedWeights().get( group ), true, "g=" + i + " weightsNormDecon", 0, 2 ).show();

			allWeightsNormed.add( fusion.getNormalizedWeights().get( group ) );

			// might not work if caching/copying is done before calling this method
			if ( FusedRandomAccessibleInterval.class.isInstance( fusion.getImages().get( group ) ) )
			{
				final long[] dim = new long[ fusion.getDownsampledBoundingBox().numDimensions() ];
				fusion.getDownsampledBoundingBox().dimensions( dim );
	
				DisplayImage.getImagePlusInstance(
						new CombineWeightsRandomAccessibleInterval(
								new FinalInterval( dim ),
								((FusedRandomAccessibleInterval)fusion.getImages().get( group )).getWeights(),
								CombineType.SUM ),
						true,
						"g=" + i + " weightsFusion",
						0, 1 ).show();
			}

			++i;
		}

		// display the sum of all normed weights
		final long[] dim = new long[ fusion.getDownsampledBoundingBox().numDimensions() ];
		fusion.getDownsampledBoundingBox().dimensions( dim );

		DisplayImage.getImagePlusInstance(
				new CombineWeightsRandomAccessibleInterval(
						new FinalInterval( dim ),
						allWeightsNormed,
						CombineType.SUM ),
				true,
				"sum of all normed weights",
				0, 1 ).show();
	}
}
