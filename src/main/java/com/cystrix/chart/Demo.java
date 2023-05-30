package com.cystrix.chart;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.DefaultXYDataset;


public class Demo{
    private static final int INTERVAL = 1000; // 更新间隔，单位为毫秒

    public static void main(String[] args) {

        // 创建一个数据集
        DefaultXYDataset dataset = new DefaultXYDataset();
        double[][] data = { { 1, 2, 3, 4, 5 }, { 5, 4, 3, 2, 1 } };
        dataset.addSeries("Series 1", data);

        // 创建一个曲线统计图
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Line Chart", // 图表标题
                "X", // X轴标签
                "Y", // Y轴标签
                dataset, // 数据集
                PlotOrientation.VERTICAL, // 图表方向
                true, // 是否显示图例
                true, // 是否生成工具提示
                false // 是否生成URL链接
        );

        // 获取图表的绘图区域（Plot）
        XYPlot plot = (XYPlot) chart.getPlot();

        // 修改绘图区域的属性
        plot.setBackgroundPaint(null); // 设置绘图区域的背景色为透明

        // 显示图表
        ChartFrame frame = new ChartFrame("Line Chart Example", chart);
        frame.pack();
        frame.setVisible(true);
    }
}
