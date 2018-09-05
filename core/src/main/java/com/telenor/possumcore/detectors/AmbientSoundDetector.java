package com.telenor.possumcore.detectors;

import android.Manifest;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.google.gson.JsonArray;
import com.telenor.possumcore.PossumCore;
import com.telenor.possumcore.abstractdetectors.AbstractDetector;
import com.telenor.possumcore.constants.DetectorType;
import com.telenor.possumcore.interfaces.IDetectorChange;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Uses microphone for ambient sound analysis. Removes voices and uses background noise with
 * diverse filters to weight the background waves. Cannot be converted back into audio after
 * weighting.
 */
public class AmbientSoundDetector extends AbstractDetector {
    private AudioManager audioManager;
    private AudioRecord audioRecorder;
    private Handler audioHandler;
    private final int bufferSize;
    //private final int recordingSamples;
    private boolean disabledMute;
    private static int lpc_dimensions = 10;
    //private static final long maxListeningTime = 12000; // Max time to listen in milliseconds

    /**
     * number of points
     */
    private static int numPoints;
    /**
     * real part
     */
    private static double real[];
    /**
     * imaginary part
     */
    private static double imag[];

    /**
     * sample rate in Hz. Should be confirmed use with @alex
     */
    private final static double samplingRate = 16000.0;

    /**
     * number of mel filters (SPHINX-III uses 40)
     */
    private final static int numMelFilters = 23;
    /**
     * lower limit of filter (or 64 Hz?)
     */
    private final static double lowerFilterFreq = 133.3334;
    /**
     * Number of MFCCs per frame
     */
    private final static int numCepstra = 13;
    private static int number_of_features = numCepstra + lpc_dimensions + 6;
    /**
     * Number of samples per frame
     */
    private final static int frameLength = 512;

    /**
     * FFT Size (Must be be a power of 2)
     */
    private final static int fftSize = frameLength;

    public AmbientSoundDetector(@NonNull Context context) {
        this(context, null);
    }
    /**
     * Constructor for all ambient sound detectors.
     *
     * @param context a valid android context
     */
    public AmbientSoundDetector(@NonNull Context context, IDetectorChange listener) {
        super(context, listener);
        // TODO: Clean up class and refactor
//        recordingSamples = (int)(sampleRate() * (maxListeningTime / 1000));
        bufferSize = AudioTrack.getMinBufferSize(sampleRate(), AudioFormat.CHANNEL_OUT_MONO, audioEncoding());
        audioHandler = new Handler(Looper.getMainLooper());
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioRecorder = getAudioRecord();
    }
    // TODO: FeatureExtractor uses sampling rate of 16000, but it is recorded with sampleRate of 48000. Check with Alex.

    @Override
    public int queueLimit(@NonNull String key) {
        return 200; // Default set
    }

    /**
     * The presently used sampleRate in Hertz. Override to change
     *
     * @return int value of present sampleRate
     */
    private int sampleRate() {
        return 48000; // Was originally 48k, but transformation code is 16k
    }

    private AudioRecord getAudioRecord() {
        return new AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRate(),
                AudioFormat.CHANNEL_IN_MONO,
                audioEncoding(),
                bufferSize);
    }

    /**
     * The presently used audio encoding. Override to change
     *
     * @return value of encoding used
     */
    private int audioEncoding() {
        return AudioFormat.ENCODING_PCM_16BIT;
    }

    /**
     * Stops recording if it is already recording
     */
    private void stopRecording() {
        if (isRecording()) {
            audioRecorder.stop();
        }
    }

    /**
     * Returns whether it is actually recording
     *
     * @return true if it is recording
     */
    private boolean isRecording() {
        return audioRecorder != null && audioRecorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING;
    }

    @Override
    public boolean isEnabled() {
        return audioManager != null;
    }

    @Override
    public void run() {
        super.run();
        if (isEnabled() && isAvailable() && !isRecording()) {
            short[] buffer = new short[bufferSize];
            int readSize;
            if (isMuted() && audioManager != null) {
                audioManager.setMicrophoneMute(false);
                disabledMute = true;
            }
            if (audioRecorder == null || audioRecorder.getState() == AudioRecord.STATE_UNINITIALIZED) {
                audioRecorder = getAudioRecord();
            }
            audioRecorder.startRecording();
            while (isRecording()) {
                if ((readSize = audioRecorder.read(buffer, 0, bufferSize)) != AudioRecord.ERROR_INVALID_OPERATION) {
                    // Calculate features
                    for (double[] window : getFeaturesFromSample(buffer, readSize, sampleRate())) {
                        streamData(writeFeatureWindowToJsonArray(window));
                    }
                }
            }
        } else {
            PossumCore.addLogEntry(context(), "Unable to start audio:"+isEnabled()+","+isAvailable()+","+isRecording());
        }
    }

    /**
     * Handy function for finding out if the mic is muted
     *
     * @return true if muted, false if not
     */
    private boolean isMuted() {
        return audioManager.isMicrophoneMute();
    }

    @Override
    public int detectorType() {
        return DetectorType.Audio;
    }

    @Override
    public String detectorName() {
        return "sound";
    }

    @Override
    public String requiredPermission() {
        return Manifest.permission.RECORD_AUDIO;
    }

    @Override
    public void terminate() {
        audioHandler.removeCallbacks(this);
        if (disabledMute) {
            // Since I disabled mute to record, here I re-enable mute
            audioManager.setMicrophoneMute(true);
            disabledMute = false;
        }
        stopRecording();
    }

    /**
     * Takes a sound sample, divides it into partially overlapping windows, and returns an array of
     * sound features for every window.
     *
     * @param samples       Audio sample in the PCM 16 bit format
     * @param sample_size   Size of the audio sample
     * @param sampling_rate Sampling rate (Hz)
     * @return List of audio samples of length windows. Each item in the list is an array of
     * of length number_of_features.
     */
    private static List<double[]> getFeaturesFromSample(short[] samples, int sample_size,
                                                        int sampling_rate) {
        final float window_overlap = 0.5f;
        // Convert source to double and absolute values (for SoundFeatureExtractor)
        double[] samples_double_abs = new double[sample_size];
        double[] samples_double = new double[sample_size];
        for (int i = 0; i < sample_size; i++) {
            samples_double_abs[i] = Math.abs((double) samples[i]);
            samples_double[i] = (double) samples[i];
        }
        List<double[]> features = new ArrayList<>();
        long time = DateTime.now().getMillis();
        int current = 0;
        // Window size hardcoded since it need to be a power of 2 for FFT
        //int window_size = 4096; // This is a window of 85 ms @ 48000 hz sample rate
        int window_size = 2048; // This is a window of 46 ms @ 44100 hz sample rate
        //Log.i(tag, "AP: current+windowSize="+(current+window_size)+", sample size:"+sample_size);
        while (current + window_size <= sample_size) {
            try {
                double[] window_data_abs = Arrays.copyOfRange(samples_double_abs,
                        current, current + window_size);
                double[] window_data = Arrays.copyOfRange(samples_double,
                        current, current + window_size);
                // Get SoundFeatureExtractor features
                double[] mfcc_features = get_mfcc(window_data_abs, (double) sampling_rate);
                // Get LPC features
                double[] lpc_features = get_lpc(window_data);
                // Get time-domain features
                double zcr = get_zcr(window_data);
                double ste = get_ste(window_data);
                // Compute FFT and get frequency domain features
                computeFFT(window_data);
                double[] fft_real = real;
                double[] fft_imag = imag;
                double[] fft = new double[fft_real.length];
                // Convert complex number to magnitude
                for (int j = 0; j < fft_real.length; j++) {
                    fft[j] = Math.sqrt(fft_imag[j] * fft_imag[j] + fft_real[j] * fft_real[j]);
                }
                // Get frequency domain features
                double sc = get_sc(fft);
                double peak = get_peak(fft);
                double sf = get_flatness(fft);
                double[] tmp = new double[number_of_features];
                // Make time the first column
                tmp[0] = time;
                // Copy in features to result array
                System.arraycopy(mfcc_features, 0, tmp, 1, numCepstra);
                tmp[numCepstra + 1] = zcr;
                tmp[numCepstra + 2] = ste;
                tmp[numCepstra + 3] = sc;
                tmp[numCepstra + 4] = sf;
                tmp[numCepstra + 5] = peak;
                System.arraycopy(lpc_features, 0, tmp, numCepstra + 6, lpc_dimensions);
                // Add to list of features
                features.add(tmp);
            } catch (Exception e) {
                e.printStackTrace();
            }
            current += window_size * (1 - window_overlap);
        }
        return features;
    }

    /**
     * performs Fast Fourier Transformation<br>
     * calls: none<br>
     * called by: featureExtraction
     * @param signal the input to perform FFT on
     */
    private static void computeFFT(double signal[]){
        numPoints = signal.length;

        // initialize real & imag array
        real = new double[numPoints];
        imag = new double[numPoints];

        // move the N point signal into the real part of the complex DFT's time domain
        real = signal;

        // set all of the samples in the imaginary part to zero
        for (int i = 0; i < imag.length; i++){
            imag[i] = 0;
        }

        // perform FFT using the real & imag array
        FFT();
    }

    /**
     * performs Fast Fourier Transformation<br>
     * calls: none<br>
     * called by: fft
     */
    private static void FFT(){
        if (numPoints == 1) return;

        final double pi = Math.PI;
        final int numStages = (int)(Math.log(numPoints) / Math.log(2));

        int halfNumPoints = numPoints >> 1;
        int j = halfNumPoints;

        // FFT time domain decomposition carried out by "bit reversal sorting" algorithm
        int k;
        for (int i = 1; i < numPoints - 2; i++){
            if (i < j){
                // swap
                double tempReal = real[j];
                double tempImag = imag[j];
                real[j] = real[i];
                imag[j] = imag[i];
                real[i] = tempReal;
                imag[i] = tempImag;
            }

            k = halfNumPoints;

            while ( k <= j ){
                j -= k;
                k >>=1;
            }

            j += k;
        }

        // loop for each stage
        for (int stage = 1; stage <= numStages; stage++){

            int LE = 1;
            for (int i = 0; i < stage; i++)
                LE <<= 1;

            int LE2 = LE >> 1;
            double UR = 1;
            double UI = 0;

            // calculate sine & cosine values
            double SR = Math.cos( pi / LE2 );
            double SI = -Math.sin( pi / LE2 );

            // loop for each sub DFT
            for (int subDFT = 1; subDFT <= LE2; subDFT++){

                // loop for each butterfly
                for (int butterfly = subDFT - 1; butterfly <= numPoints - 1; butterfly+=LE){
                    int ip = butterfly + LE2;

                    // butterfly calculation
                    double tempReal = real[ip] * UR - imag[ip] * UI;
                    double tempImag = real[ip] * UI + imag[ip] * UR;
                    real[ip] = real[butterfly] - tempReal;
                    imag[ip] = imag[butterfly] - tempImag;
                    real[butterfly] += tempReal;
                    imag[butterfly] += tempImag;
                }

                double tempUR = UR;
                UR = tempUR * SR - UI * SI;
                UI = tempUR * SI + UI * SR;
            }
        }
    }

    /**
     * Returns the Short-time Energy of a supplied audio sample
     *
     * @param samples The audio signal for the sample
     * @return Short-time Energy
     */
    private static double get_ste(double[] samples) {
        double ste = 0.0;
        for (double sample : samples) {
            ste += sample * sample;
        }
        return ste / samples.length;
    }
    /**
     * Returns the spectral peak, i.e. the frequency with the largest amplitude
     *
     * @param spectrum The Fourier spectrum of the audio sample
     * @return Spectral Peak
     */
    private static double get_peak(double[] spectrum) {
        double max = 0.0;
        int peak = 0;
        for (int i = 0; i < spectrum.length; i++) {
            if (spectrum[i] > max) {
                peak = i;
                max = spectrum[i];
            }
        }
        return peak;
    }

    /**
     * Returns the spectral flatness of a supplied audio sample
     *
     * @param spectrum The Fourier spectrum of the audio sample
     * @return Spectral Flatness
     */
    private static double get_flatness(double[] spectrum) {
        double sum = 0.0;
        double sumln = 0.0;
        double denom = 1.0 / spectrum.length;
        for (double aSpectrum : spectrum) {
            sum += aSpectrum;
            sumln += Math.log(aSpectrum);
        }
        return Math.exp(denom * sumln) / denom / sum;
    }

    /**
     * Returns the Spectral Centroid of the supplied audio sample
     *
     * @param spectrum The Fourier spectrum of the audio sample
     * @return Spectral Centroid
     */
    private static double get_sc(double[] spectrum) {
        double num = 0.0;
        double den = 0.0;
        for (int i = 0; i < spectrum.length; i++) {
            num += i * spectrum[i];
            den += spectrum[i];
        }
        return num / den;
    }

    /**
     * Returns the MFCC features of a supplied audio sample
     *
     * @param samples       The audio signal for the sample
     * @param sampling_rate The sampling rate in Hz
     * @return MFCC coefficients
     */
    private static double[] get_mfcc(double[] samples, double sampling_rate) {
        int[] cbin = fftBinIndices(sampling_rate);
        double[] fbank = melFilter(samples, cbin);
        double[] nonLinearTransformation = nonLinearTransformation(fbank);
        return cepCoefficients(nonLinearTransformation);
    }

    /**
     * Returns the Zero Crossing Rate of a supplied audio sample
     *
     * @param samples The audio signal for the sample
     * @return Zero Crossing Rate
     */
    private static double get_zcr(double[] samples) {
        double zcr = 0.0;
        for (int i = 1; i < samples.length; i++) {
            zcr += Math.abs(Math.signum(samples[i]) - Math.signum(samples[i - 1]));
        }
        return zcr / samples.length;
    }

    /**
     * Returns the Linear Prediction Cepstral coefficients
     *
     * @param samples The audio signal for the sampele
     * @return LPC coefficients (lenght lpc_dimensions)
     */
    private static double[] get_lpc(double[] samples) {
        final double lambda = 0.0;
        // find the order-P autocorrelation array, R, for the sequence x of
        // length L and warping of lambda
        // Autocorrelate(&pfSrc[stIndex],siglen,R,P,0);

        double[] R = new double[lpc_dimensions + 1];
        double K[] = new double[lpc_dimensions];
        double A[] = new double[lpc_dimensions];
        double[] dl = new double[samples.length];
        double[] Rt = new double[samples.length];
        double r1, r2, r1t;
        R[0] = 0;
        Rt[0] = 0;
        r1 = 0;
        r2 = 0;
        //r1t = 0;
        for (int k = 0; k < samples.length; k++) {
            Rt[0] += samples[k] * samples[k];

            dl[k] = r1 - lambda * (samples[k] - r2);
            r1 = samples[k];
            r2 = dl[k];
        }
        for (int i = 1; i < R.length; i++) {
            Rt[i] = 0;
            r1 = 0;
            r2 = 0;
            for (int k = 0; k < samples.length; k++) {
                Rt[i] += dl[k] * samples[k];

                r1t = dl[k];
                dl[k] = r1 - lambda * (r1t - r2);
                r1 = r1t;
                r2 = dl[k];
            }
        }
        System.arraycopy(Rt, 0, R, 0, R.length);

        // LevinsonRecursion(unsigned int P, float *R, float *A, float *K)
        double Am1[] = new double[62];

        if (R[0] == 0.0) {
            for (int i = 1; i < lpc_dimensions; i++) {
                K[i] = 0.0;
                A[i] = 0.0;
            }
        } else {
            double km, Em1, Em;
            int k, s, m;
            for (k = 0; k < lpc_dimensions; k++) {
                A[k] = 0; //A[0] = 0;
                Am1[k] = 0; //Am1[0] = 0;
            }
            A[0] = 1;
            Am1[0] = 1;
            //km = 0;
            Em1 = R[0];
            for (m = 1; m < lpc_dimensions; m++) // m=2:N+1
            {
                double err = 0.0f; // err = 0;
                for (k = 1; k <= m - 1; k++)
                    // for k=2:m-1
                    err += Am1[k] * R[m - k]; // err = err + am1(k)*R(m-k+1);
                km = (R[m] - err) / Em1; // km=(R(m)-err)/Em1;
                K[m - 1] = -km;
                A[m] = km; // am(m)=km;
                for (k = 1; k <= m - 1; k++)
                    // for k=2:m-1
                    A[k] = Am1[k] - km * Am1[m - k]; // am(k)=am1(k)-km*am1(m-k+1);
                Em = (1 - km * km) * Em1; // Em=(1-km*km)*Em1;
                for (s = 0; s < lpc_dimensions; s++)
                    // for s=1:N+1
                    Am1[s] = A[s]; // am1(s) = am(s)
                Em1 = Em; // Em1 = Em;
            }
        }
        return K;
    }

    /**
     * Calculate the output of the mel filter<br>
     */
    private static double[] melFilter(double bin[], int cbin[]) {
        double temp[] = new double[numMelFilters + 2];

        for (int k = 1; k <= numMelFilters; k++) {
            double num1 = 0, num2 = 0;

            for (int i = cbin[k - 1]; i <= cbin[k]; i++) {
                num1 += ((i - cbin[k - 1] + 1) / (cbin[k] - cbin[k - 1] + 1)) * bin[i];
            }

            for (int i = cbin[k] + 1; i <= cbin[k + 1]; i++) {
                num2 += (1 - ((i - cbin[k]) / (cbin[k + 1] - cbin[k] + 1))) * bin[i];
            }

            temp[k] = num1 + num2;
        }

        double fbank[] = new double[numMelFilters];
        System.arraycopy(temp, 1, fbank, 0, numMelFilters);
        return fbank;
    }

    /**
     * Cepstral coefficients are calculated from the output of the Non-linear Transformation method<br>
     * calls: none<br>
     * called by: featureExtraction
     * @param f Output of the Non-linear Transformation method
     * @return Cepstral Coefficients
     */
    private static double[] cepCoefficients(double f[]){
        double cepc[] = new double[numCepstra];

        for (int i = 0; i < cepc.length; i++){
            for (int j = 1; j <= numMelFilters; j++){
                cepc[i] += f[j - 1] * Math.cos(Math.PI * i / numMelFilters * (j - 0.5));
            }
        }

        return cepc;
    }

    /**
     * the output of mel filtering is subjected to a logarithm function (natural logarithm)<br>
     * calls: none<br>
     * called by: featureExtraction
     * @param fbank Output of mel filtering
     * @return Natural longLog of the output of mel filtering
     */
    private static double[] nonLinearTransformation(double fbank[]){
        double f[] = new double[fbank.length];
        final double FLOOR = -50;

        for (int i = 0; i < fbank.length; i++){
            f[i] = Math.log(fbank[i]);

            // check if ln() returns a value less than the floor
            if (f[i] < FLOOR) f[i] = FLOOR;
        }

        return f;
    }

    /**
     * Returns a json array of json arrays (number of time windows, number of features). Casts all
     * numbers to string to avoid rounding.
     *
     * @param feature_list List of features
     * @return json array of json arrays containing features
     */
    private static JsonArray writeFeatureWindowToJsonArray(double[] feature_list) {
        JsonArray array = new JsonArray();
        for (int i = 0; i < number_of_features; i++) {
            if (i == 0) array.add(Long.toString((long) feature_list[i]));
            else array.add(Double.toString(feature_list[i]));
        }
        return array;
    }

    /**
     * calculates the FFT bin indices<br>
     * calls: none<br>
     * called by: featureExtraction
     *
     * @return array of FFT bin indices
     */
    private static int[] fftBinIndices(double samplingRate) {
        int cbin[] = new int[numMelFilters + 2];

        cbin[0] = (int) Math.round(lowerFilterFreq / samplingRate * fftSize);
        cbin[cbin.length - 1] = (fftSize / 2);

        for (int i = 1; i <= numMelFilters; i++) {
            double fc = centerFreq(i);

            cbin[i] = (int) Math.round(fc / samplingRate * fftSize);
        }

        return cbin;
    }

    /**
     * calculates center frequency<br>
     * calls: none<br>
     * called by: featureExtraction
     *
     * @param i Index of mel filters
     * @return Center Frequency
     */
    private static double centerFreq(int i) {
        double mel[] = new double[2];
        mel[0] = freqToMel(lowerFilterFreq);
        mel[1] = freqToMel(samplingRate / 2);

        // take inverse mel of:
        double temp = mel[0] + ((mel[1] - mel[0]) / (numMelFilters + 1)) * i;
        return inverseMel(temp);
    }

    /**
     * convert frequency to mel-frequency<br>
     * calls: none<br>
     * called by: featureExtraction
     *
     * @param freq Frequency
     * @return Mel-Frequency
     */
    private static double freqToMel(double freq) {
        return 2595 * log10(1 + freq / 700);
    }

    /**
     * calculates logarithm with base 10<br>
     * calls: none<br>
     * called by: featureExtraction
     *
     * @param value Number to take the longLog of
     * @return base 10 logarithm of the input values
     */
    private static double log10(double value) {
        return Math.log(value) / Math.log(10);
    }

    /**
     * calculates the inverse of Mel Frequency<br>
     * calls: none<br>
     * called by: featureExtraction
     */
    private static double inverseMel(double x) {
        double temp = Math.pow(10, x / 2595) - 1;
        return 700 * (temp);
    }
}