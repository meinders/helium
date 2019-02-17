/*
 * Copyright 2013-2019 Gerrit Meinders
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package helium;

import java.awt.*;
import java.io.*;
import java.nio.charset.*;
import javax.sound.sampled.*;
import javax.swing.*;

/**
 * @author Gerrit Meinders
 */
public class RecorderExperiments
{
	public static void main( final String[] args )
		throws IOException
	{
		final AudioFormat format = new AudioFormat( AudioFormat.Encoding.PCM_SIGNED,
		                                            44100, 16, 2, 4, 44100, false );

		// testRecordingAndEncoding(format);
		// testNormalization(format);
		// testStreamNormalizationAndEncoding(format);
		testGetRecordingInformation();
	}

	private static void testGetRecordingInformation()
	{
		for ( final Mixer.Info mixerInfo : AudioSystem.getMixerInfo() )
		{
			System.out.println( "Mixer '" + mixerInfo + "'" );

			final AudioFormat format = new AudioFormat( AudioFormat.Encoding.PCM_SIGNED,
			                                            44100, 16, 2, 4, 44100, false );
			try
			{
				final TargetDataLine targetDataLine = AudioSystem.getTargetDataLine( format, mixerInfo );
				System.out.println( " - " + targetDataLine );
			}
			catch ( final Exception e )
			{
				System.out.println( " - EXCEPTION: " + e.getMessage() );
			}
		}

		final Line.Info[] targetLineInfos = AudioSystem.getTargetLineInfo( new Line.Info(
			TargetDataLine.class ) );

		for ( final Line.Info info : targetLineInfos )
		{
			final DataLine.Info dataLineInfo = (DataLine.Info)info;
			System.out.println( "info = '" + info + "'" );

			final TargetDataLine line;
			try
			{
				line = (TargetDataLine)AudioSystem.getLine( dataLineInfo );
			}
			catch ( final LineUnavailableException e )
			{
				System.out.println( "Failed to get: " + e.getMessage() );
				continue;
			}

			for ( final AudioFormat format : dataLineInfo.getFormats() )
			{
				System.out.println( " - " + format );
				final AudioFormat someFormat = new AudioFormat( format.getEncoding(),
				                                                44100, format.getSampleSizeInBits(),
				                                                format.getChannels(), format.getFrameSize(), 44100,
				                                                format.isBigEndian() );

				try
				{
					line.open( someFormat );
					line.close();
				}
				catch ( final LineUnavailableException e )
				{
					System.out.println( "Failed to open: " + e.getMessage() );
				}
			}
		}
	}

	public static void testRollingMaximum()
		throws IOException
	{
		final int dataSize = 44100 * 100;
		final int windowSize = 44100;

		System.out.println( "Generating test data..." );
		final int[] values = new int[ dataSize ];
		for ( int i = 0; i < values.length; i++ )
		{
			values[ i ] = (int)( Math.sin( 0.000023 * i ) * 4000 + Math.sin( 0.0017 * i ) * 1000 );
		}

		System.out.println( "Calculating rolling maximum..." );
		final long start = System.nanoTime();

		final int[] maximum = new int[ values.length ];
		final RollingMaximum rollingMaximum = new RollingMaximum( windowSize );
		for ( int j = 0; j < values.length; j++ )
		{
			if ( j >= windowSize )
			{
				rollingMaximum.remove( values[ j - windowSize ] );
			}
			rollingMaximum.add( values[ j ] );
			maximum[ j ] = rollingMaximum.get();
		}

		final long end = System.nanoTime();
		System.out.println( "Duration: " + ( ( end - start ) / 1000000000.0 )
		                    + " seconds" );
	}

	public static void testNormalization( final AudioFormat format )
		throws IOException
	{
		System.out.println( "Loading source file..." );
		final File source = new File( "D:\\Code\\Java\\Eclipse\\opwViewer\\test.wav" );
		final File result = new File(
			"D:\\Code\\Java\\Eclipse\\opwViewer\\test.output.wav" );
		final InputStream in = new BufferedInputStream( new FileInputStream( source ) );
		final DataOutputStream out = new DataOutputStream( new BufferedOutputStream(
			new FileOutputStream( result ) ) );
		final ByteArrayOutputStream bufferOut = new ByteArrayOutputStream(
			(int)source.length() );

		/* Skip header */
		copyUntil( in, out, "data", 4 );

		/* Read several seconds */
		int read;
		while ( ( read = in.read() ) != -1 )
		{
			bufferOut.write( read );
		}

		in.close();
		bufferOut.flush();
		bufferOut.close();
		final byte[] data = bufferOut.toByteArray();
		System.out.println( "Done." );

		/*
		 * Subdivide into frames and determine maximum per frame
		 */
		final boolean bigEndian = format.isBigEndian();
		final int bytesPerSample = format.getSampleSizeInBits() / 8;
		final int channels = format.getChannels();
		final int samples = data.length / ( channels * bytesPerSample );
		final int maxValue = ( 1 << ( format.getSampleSizeInBits() - 1 ) ) - 1;

		final int frameSize = 44100;// 1.0s / ...
		final int frameCount = samples / frameSize;
		final int[] frameMaximum = new int[ frameCount ];

		for ( int frameIndex = 0; frameIndex < frameCount; frameIndex++ )
		{
			for ( int frameOffset = 0; frameOffset < frameSize; frameOffset++ )
			{
				for ( int channel = 0; channel < channels; channel++ )
				{
					final int sampleIndex = ( frameIndex * frameSize + frameOffset );
					final int base = ( sampleIndex * channels + channel )
					                 * bytesPerSample;
					final int sample = bigEndian ? (short)( data[ base ] << 8 | data[ base + 1 ] )
					                             : (short)( data[ base + 1 ] << 8 | data[ base ] );

					final int absSample = Math.abs( sample );
					if ( absSample > frameMaximum[ frameIndex ] )
					{
						frameMaximum[ frameIndex ] = absSample;
					}
				}
			}
		}

		final double maxGain = 20.0; // 10dB
		final double maxGainIncrease = 1.1; // +10%

		double startGain;
		double endGain = 1.0;
		for ( int frameIndex = 0; frameIndex < frameCount; frameIndex++ )
		{
			final int startMaximum = ( frameIndex == 0 ) ? frameMaximum[ frameIndex ]
			                                             : Math.max( frameMaximum[ frameIndex - 1 ],
			                                                         frameMaximum[ frameIndex ] );
			final int endMaximum = ( frameIndex == frameCount - 1 ) ? frameMaximum[ frameIndex ]
			                                                        : Math.max( frameMaximum[ frameIndex ],
			                                                                    frameMaximum[ frameIndex + 1 ] );

			final double maxStartGain = Math.min( endGain * maxGainIncrease, maxGain );
			startGain = Math.min(
				Math.max( 1.0, ( maxValue * 0.97 ) / startMaximum ),
				maxStartGain );
			final double maxEndGain = Math.min( startGain * maxGainIncrease, maxGain );
			endGain = Math.min( Math.max( 1.0, ( maxValue * 0.97 ) / endMaximum ),
			                    maxEndGain );

			System.out.println( "Frame " + frameIndex );
			System.out.println( " - startLevel = '" + startGain + "'" );
			System.out.println( " - endLevel = '" + endGain + "'" );

			for ( int frameOffset = 0; frameOffset < frameSize; frameOffset++ )
			{
				final double gain = startGain + ( endGain - startGain ) * frameOffset
				                                / frameSize;

				for ( int channel = 0; channel < channels; channel++ )
				{
					final int sampleIndex = ( frameIndex * frameSize + frameOffset );
					final int base = ( sampleIndex * channels + channel )
					                 * bytesPerSample;
					final int sample = bigEndian ? (short)( data[ base ] << 8 | data[ base + 1 ] & 0xff )
					                             : (short)( data[ base + 1 ] << 8 | data[ base ] & 0xff );

					writeShortLE( out, (int)( sample * gain ) );
				}
			}
		}

		out.close();

		// AudioClipView view = new AudioClipView(format, data);
		// JFrame frame = new JFrame();
		// frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		// frame.setLayout(new BorderLayout());
		// frame.add(view);
		// frame.setSize(new Dimension(1000, 400));
		// frame.setVisible(true);
	}

	private static void testStreamNormalizationAndEncoding( final AudioFormat format )
		throws IOException
	{
		final File source = new File( "D:\\Code\\Java\\Eclipse\\opwViewer\\test3.wav" );
		final File result = new File(
			"D:\\Code\\Java\\Eclipse\\opwViewer\\test3.output.mp3" );

		System.out.println( "source = '" + source + "'" );
		System.out.println( "result = '" + result + "'" );

		try ( final InputStream in = new BufferedInputStream( new FileInputStream( source ) );
		      final OutputStream out = new BufferedOutputStream( new FileOutputStream( result ) ) )
		{

			final Mp3Options options = new Mp3Options();
			options.setExecutable( new File( "D:\\lame-3.96.1\\lame.exe" ) );

			final OutputStream encoder = new LameOutputStream( out, options );

			copyUntil( in, encoder, "data", 4 );
			encoder.flush();

			final OutputStream normalizer = new NormalizingOutputStream( encoder,
			                                                             format, 2.0, 30.0, true );

			final Pipe pipe = new Pipe( in, normalizer );
			pipe.call();

		}

		System.out.println( "Done." );
	}

	private static void copyUntil( final InputStream in, final OutputStream out,
	                               final String string, final int offset )
		throws IOException
	{
		if ( offset < 0 )
		{
			throw new IllegalArgumentException(
				"offset < 0: do i look like a magician?" );
		}

		final byte[] bytes;
		bytes = string.getBytes( StandardCharsets.UTF_8 );

		int matchLength = 0;
		while ( matchLength != bytes.length )
		{
			final int read = in.read();
			if ( read == -1 )
			{
				throw new EOFException();
			}
			out.write( read );
			if ( read == bytes[ matchLength ] )
			{
				matchLength++;
			}
			else
			{
				matchLength = 0;
			}
		}

		for ( int i = 0; i < offset; i++ )
		{
			final int read = in.read();
			if ( read == -1 )
			{
				throw new EOFException();
			}
			out.write( read );
		}
	}

	private static void testRecordingAndEncoding( final AudioFormat format )
		throws IOException
	{
		final TargetDataLine targetDataLine;

		final Line.Info[] targetLineInfos = AudioSystem.getTargetLineInfo( new DataLine.Info(
			TargetDataLine.class, format ) );
		if ( targetLineInfos.length == 0 )
		{
			System.out.println( "No suitable audio device found." );
			return;
		}

		try
		{
			targetDataLine = (TargetDataLine)AudioSystem.getLine( targetLineInfos[ 0 ] );
		}
		catch ( final LineUnavailableException e )
		{
			e.printStackTrace();
			return;
		}

		final Runtime runtime = Runtime.getRuntime();
		final Process encoder = runtime.exec( new String[] {
			"D:\\lame-3.96.1\\lame.exe", "-b128", "-h", "-mj", "-"
		} );

		final OutputStream encoderInput = encoder.getOutputStream();
		final InputStream encoderOutput = encoder.getInputStream();
		final InputStream encoderError = encoder.getErrorStream();

		final File outputFile = new File(
			"D:\\Code\\C++\\Automatic Gain Control\\java-recording.mp3" );
		final DataOutputStream out = new DataOutputStream(
			new BufferedOutputStream( encoderInput ) );
		final OutputStream fileOut = new BufferedOutputStream(
			new FileOutputStream( outputFile ) );

		final Thread outPipeThread = new Thread( new Pipe( encoderOutput, fileOut ) );
		outPipeThread.start();

		final Thread errPipeThread = new Thread( new Pipe( encoderError, System.err ) );
		errPipeThread.start();

		out.writeBytes( "RIFF" );
		// file size (counting from the next byte)
		writeIntLE( out, 0x80000024 ); // unknown
		out.writeBytes( "WAVE" );

		out.writeBytes( "fmt " );
		writeIntLE( out, 16 ); // chunk size (counting from the next byte)
		writeShortLE( out, 1 ); // audio format = PCM
		writeShortLE( out, 2 ); // number of channels
		writeIntLE( out, 44100 ); // sample rate
		// byte rate = sample*channels*bits/8
		writeIntLE( out, 44100 * 2 * 16 / 8 );
		writeShortLE( out, 2 * 16 / 8 ); // block align = channels*bits/8
		writeShortLE( out, 16 ); // bits per sample

		out.writeBytes( "data" );
		// chunk size (counting from the next byte)
		writeIntLE( out, 0x80000000 ); // unknown

		try
		{
			targetDataLine.open( format );
			final byte[] buffer = new byte[ targetDataLine.getBufferSize() / 5 ];

			final OutputStream dataOut = new NormalizingOutputStream( out, format,
			                                                          2.0, 30.0, true );

			System.out.println( "Recording..." );
			targetDataLine.start();
			do
			{
				final int read = targetDataLine.read( buffer, 0, buffer.length );
				for ( int i = 0; i < read; i++ )
				{
					dataOut.write( buffer[ i ] );
				}
				System.out.println( targetDataLine.available() + " / "
				                    + targetDataLine.getBufferSize() );
				if ( System.in.available() != 0 )
				{
					SwingUtilities.invokeLater( () -> {
						System.out.println( "Stop" );
						targetDataLine.stop();
						System.out.println( "Drain" );
						targetDataLine.drain();
					} );
				}
			}
			while ( targetDataLine.isRunning() );
			System.out.println( "Done." );

			targetDataLine.close();

			dataOut.close();

		}
		catch ( final LineUnavailableException e )
		{
			e.printStackTrace();
		}
	}

	private static void writeShortLE( final DataOutputStream out, final int s )
		throws IOException
	{
		out.writeByte( s );
		out.writeByte( s >> 8 );
	}

	private static void writeIntLE( final DataOutputStream out, final int i )
		throws IOException
	{
		out.writeByte( i );
		out.writeByte( i >> 8 );
		out.writeByte( i >> 16 );
		out.writeByte( i >> 24 );
	}

	private static short getLittleEndianShort( final byte[] buffer, final int offset )
	{
		return (short)( buffer[ offset ] & 0xff | buffer[ offset + 1 ] << 8 );
	}

	private static class AudioClipView
		extends JPanel
	{
		private final AudioFormat format;

		private final byte[] data;

		public AudioClipView( final AudioFormat format, final byte[] data )
		{
			if ( format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED )
			{
				throw new IllegalArgumentException( "Unsupported encoding: "
				                                    + format.getEncoding() );
			}

			this.format = format;
			this.data = data;

			setOpaque( true );
		}

		@Override
		protected void paintComponent( final Graphics g )
		{
			super.paintComponent( g );

			final boolean bigEndian = format.isBigEndian();
			final int bytesPerSample = format.getSampleSizeInBits() / 8;
			final int channels = format.getChannels();
			final int samples = data.length / ( channels * bytesPerSample );
			final int complement = -1 << format.getSampleSizeInBits();
			final int maxValue = ( 1 << ( format.getSampleSizeInBits() - 1 ) ) - 1;

			System.out.println( "bytesPerSample = '" + bytesPerSample + "'" );
			System.out.println( "complement = '"
			                    + Integer.toHexString( complement ) + "'" );
			System.out.println( "maxValue = '" + Integer.toHexString( maxValue )
			                    + "'" );

			final int width = getWidth();
			final int height = getHeight();

			final int offset = height / ( 2 * channels );

			final Color[] colors = { Color.RED, Color.BLUE };

			for ( int channel = 0; channel < channels; channel++ )
			{
				final int[] values = new int[ width ];

				g.setColor( colors[ channel ] );

				final int center = height * ( 2 * channel + 1 ) / ( 2 * channels );

				for ( int sampleIndex = 0; sampleIndex < samples; sampleIndex++ )
				{
					final int base = ( sampleIndex * channels + channel )
					                 * bytesPerSample;
					final int sample = bigEndian ? (short)( data[ base ] << 8 | data[ base + 1 ] )
					                             : (short)( data[ base + 1 ] << 8 | data[ base ] );

					final int index = sampleIndex * width / samples;
					if ( Math.abs( sample ) > Math.abs( values[ index ] ) )
					{
						values[ index ] = sample;
					}
				}

				{
					int clipValue = 0;

					for ( int x = 0; x < width; x++ )
					{
						if ( Math.abs( values[ x ] ) > Math.abs( clipValue ) )
						{
							clipValue = values[ x ];
						}
					}

					for ( int x = 1; x < width; x++ )
					{
						final int highDy1 = values[ x - 1 ] * offset / clipValue;
						final int highDy2 = values[ x ] * offset / clipValue;
						g.drawLine( x, center - highDy1, x, center - highDy2 );
					}
				}
			}

			/*
			 * Subdivide into frames and determine maximum per frame
			 */
			final int frameSize = 44100;
			final int frameCount = samples / frameSize;
			final int[] frameMaximum = new int[ frameCount ];
			int clipMaximum = 0;

			for ( int channel = 0; channel < channels; channel++ )
			{
				for ( int frameIndex = 0; frameIndex < frameCount; frameIndex++ )
				{
					for ( int frameOffset = 0; frameOffset < frameSize; frameOffset++ )
					{
						final int sampleIndex = ( frameIndex * frameSize + frameOffset );
						final int base = ( sampleIndex * channels + channel )
						                 * bytesPerSample;
						final int sample = bigEndian ? (short)( data[ base ] << 8 | data[ base + 1 ] )
						                             : (short)( data[ base + 1 ] << 8 | data[ base ] );

						final int absSample = Math.abs( sample );
						if ( absSample > frameMaximum[ frameIndex ] )
						{
							frameMaximum[ frameIndex ] = absSample;
							if ( absSample > clipMaximum )
							{
								clipMaximum = absSample;
							}
						}
					}
				}
			}

			for ( int channel = 0; channel < channels; channel++ )
			{
				g.setColor( colors[ channel ] );

				final int center = height * ( 2 * channel + 1 ) / ( 2 * channels );

				g.setColor( Color.BLACK );
				for ( int i = 1; i < frameCount - 1; i++ )
				{
					final int x1 = i * width / frameCount;
					final int x2 = ( i + 1 ) * width / frameCount;

					final int dy1 = frameMaximum[ i - 1 ] * offset / clipMaximum;
					final int dy2 = frameMaximum[ i ] * offset / clipMaximum;
					final int dy3 = frameMaximum[ i + 1 ] * offset / clipMaximum;

					final int dy12 = Math.max( dy1, dy2 );
					final int dy23 = Math.max( dy2, dy3 );

					g.drawLine( x1, center - dy12, x2, center - dy23 );
					g.drawLine( x1, center + dy12, x2, center + dy23 );
				}

				g.setColor( new Color( 0x00c000 ) );
				for ( int i = 1; i < frameCount - 1; i++ )
				{
					final int x1 = i * width / frameCount;
					final int x2 = ( i + 1 ) * width / frameCount;
					final int max12 = Math.max( frameMaximum[ i - 1 ], frameMaximum[ i ] );
					final int max23 = Math.max( frameMaximum[ i ], frameMaximum[ i + 1 ] );

					final int amp1 = offset * maxValue / max12 / 10;
					final int amp2 = offset * maxValue / max23 / 10;

					g.drawLine( x1, center - amp1, x2, center - amp2 );
					g.drawLine( x1, center + amp1, x2, center + amp2 );
				}
			}
		}
	}
}
