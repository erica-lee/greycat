package org.kevoree.modeling.util.maths.structure.blas.impl;

import org.kevoree.modeling.util.maths.structure.KArray2D;
import org.kevoree.modeling.util.maths.structure.blas.KBlas;
import org.kevoree.modeling.util.maths.structure.blas.KBlasTransposeType;

public class JavaBlas implements KBlas {
    public static int BLOCK_WIDTH = 60;
    public static int TRANSPOSE_SWITCH = 375;

    @Override
    public void dscal(double alpha, KArray2D matA) {
        if (alpha == 0) {
            matA.setAll(0);
        }
        for (int i = 0; i < matA.rows() * matA.columns(); i++) {
            matA.setAtIndex(i, alpha * matA.getAtIndex(i));
        }
    }

    @Override
    public void dgemm(KBlasTransposeType transa, KBlasTransposeType transb, double alpha, KArray2D matA, KArray2D matB, double beta, KArray2D matC) {
        mult_small(alpha, matA, matB, beta, matC); //todo to optimize later
    }

    @Override
    public void trans(KArray2D matA, KArray2D result) {
        if (matA.columns() == matA.rows()) {
            transposeSquare(matA, result);
        } else if (matA.columns() > TRANSPOSE_SWITCH && matA.rows() > TRANSPOSE_SWITCH) {
            transposeBlock(matA, result);
        } else {
            transposeStandard(matA, result);
        }
    }

    @Override
    public void shutdown() {

    }

    private void transposeSquare(KArray2D matA, KArray2D result) {
        int index = 1;
        int indexEnd = matA.columns();
        for (int i = 0; i < matA.rows();
             i++, index += i + 1, indexEnd += matA.columns()) {
            int indexOther = (i + 1) * matA.columns() + i;
            int n = i * (matA.columns() + 1);
            result.setAtIndex(n, matA.getAtIndex(n));
            for (; index < indexEnd; index++, indexOther += matA.columns()) {
                result.setAtIndex(index, matA.getAtIndex(indexOther));
                result.setAtIndex(indexOther, matA.getAtIndex(index));
            }
        }
    }

    private void transposeStandard(KArray2D matA, KArray2D result) {
        int index = 0;
        for (int i = 0; i < result.columns(); i++) {
            int index2 = i;
            int end = index + result.rows();
            while (index < end) {
                result.setAtIndex(index++, matA.getAtIndex(index2));
                index2 += matA.rows();
            }
        }
    }

    private void transposeBlock(KArray2D matA, KArray2D result) {
        for (int j = 0; j < matA.columns(); j += BLOCK_WIDTH) {
            int blockWidth = Math.min(BLOCK_WIDTH, matA.columns() - j);
            int indexSrc = j * matA.rows();
            int indexDst = j;

            for (int i = 0; i < matA.rows(); i += BLOCK_WIDTH) {
                int blockHeight = Math.min(BLOCK_WIDTH, matA.rows() - i);
                int indexSrcEnd = indexSrc + blockHeight;

                for (; indexSrc < indexSrcEnd; indexSrc++) {
                    int colSrc = indexSrc;
                    int colDst = indexDst;
                    int end = colDst + blockWidth;
                    for (; colDst < end; colDst ++) {
                        result.setAtIndex(colDst, matA.getAtIndex(colSrc));
                        colSrc+=matA.rows();
                    }
                    indexDst += result.rows();
                }

            }

        }
    }


    private void mult_small(double alpha, KArray2D matA, KArray2D matB, double beta, KArray2D matC) {

        int cIndex = 0;
        double[] datA = matA.data();
        double[] datB = matB.data();
        double[] datC = matC.data();


        for (int j = 0; j < matB.columns(); j++) {

            for (int i = 0; i < matA.rows(); i++) {
                double total = 0;
                int indexA = i;
                int indexB = j * matB.rows();
                int end = indexA + (matB.rows() - 1) * matA.rows();
                while (indexA <= end) {
                    total += datA[indexA] * datB[indexB];
                    indexA += matA.rows();
                    indexB++;
                }
                datC[cIndex] = alpha * total + beta * matC.getAtIndex(cIndex);
                cIndex++;
            }
        }
    }


}
