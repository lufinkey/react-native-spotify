package com.lufinkey.react.spotify;

import android.util.Log;

public class HighPassFilter {
    private HighPassFilterChannel [] hpfChannels = {
      new HighPassFilterChannel(
              new float[]{0.0f, 0.0f},
              new float[]{0.0f, 0.0f},
              1.0f,
              1.0f
      ),
      new HighPassFilterChannel(
              new float[]{0.0f, 0.0f},
              new float[]{0.0f, 0.0f},
              1.0f,
              1.0f
      )
    };

    private int mSamplingRate = 44100;

    public void setSamplingRate(int sampleRate) {
        this.mSamplingRate = sampleRate;
    }


    public void setFilter(float cutoffFrequency) {
        float   a,aw,s1,a1,gain;
        int     i,j;

        /* translate cutoff frequency in alpha 'a' */
        a = cutoffFrequency/((float)this.mSamplingRate);
        /* clip to nyquist frequency */
        if (a>=0.5) { a=0.5f; } else
        if (a< 0.0) { a=0.0f; }
        /* wrap 'a' into 'aw' */
        aw = (float) (Math.tan(a*Math.PI)/Math.PI);
        /* set spole */
        s1 = (float) (-2.0*Math.PI*aw);
        /* convert via bilinear function into zpole */
        a1 = (2.0f + s1)/(2.0f - s1);
        /* calculate gain of H(z) = (1 - z^(-1)) / (1+a1*z^(-1)) out point '-1' */
        gain = 2.0f/(1.0f + a1);

        //D("[high pass] fc [%f] fs [%f] aw [%f] a1 [%f] gain [%f]\n", fc ,fs, aw, a1, gain);

        /* set filter constants for the first two channels 0 and 1 */
        for (i=0; i<2; i++) {
            /* set filter constants */
            hpfChannels[i].setA1(a1);
            hpfChannels[i].setGain(gain);
        }
    }

    public short [] filterBlock(short[] samples, int sampleCount, int channelCount) {
        int overflowCounter = 0;
        float returnValue;

        for(int sampleIndex = 0; sampleIndex < sampleCount; sampleIndex++) {
            for(int channelIndex = 0; channelIndex < channelCount; channelIndex++) {
                int blockIndex = channelCount * sampleIndex + channelIndex;
                float inputSample = new Short(samples[blockIndex]).floatValue();
                float rv = hpfChannels[channelIndex].stepFilter(inputSample); // = this.stepFilter(inputSample, hpfChannels[channelIndex]);

                if (rv < Short.MIN_VALUE) { rv = (float)Short.MIN_VALUE; } else
                if (hpfChannels[channelIndex].getYv()[1] > Short.MAX_VALUE) { rv = (float)Short.MAX_VALUE; }

                short original = samples[blockIndex];
                samples[blockIndex] = (short) Math.round(rv);
            }
        }

        return samples;
    }


    private class HighPassFilterChannel {
        private float[] xv;
        private float[] yv;
        private float a1;
        private float gain;

        public HighPassFilterChannel() {
        }

        public HighPassFilterChannel(float[] xv, float[] yv, float a1, float gain) {
            this.xv = xv;
            this.yv = yv;
            this.a1 = a1;
            this.gain = gain;
        }

        private float stepFilter(float inputSample) {
            xv[0] = xv[1];
            xv[1] = inputSample / gain;
            yv[0] = yv[1];
            yv[1] = (xv[1] - xv[0]) + (a1 * yv[0]);
            return yv[1];
        }

        public float[] getXv() {
            return xv;
        }

        public void setXv(float[] xv) {
            this.xv = xv;
        }

        public float[] getYv() {
            return yv;
        }

        public void setYv(float[] yv) {
            this.yv = yv;
        }

        public float getA1() {
            return a1;
        }

        public void setA1(float a1) {
            this.a1 = a1;
        }

        public float getGain() {
            return gain;
        }

        public void setGain(float gain) {
            this.gain = gain;
        }


    }
}
