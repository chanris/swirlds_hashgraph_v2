package com.cystrix.chart;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.util.Timer;
import java.util.TimerTask;

public class Demo{
    private static final int INTERVAL = 1000; // 更新间隔，单位为毫秒

    public static void main(String[] args) {

        XYSeries series = new XYSeries("曲线图");
        series.setMaximumItemCount(200);

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(series);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "曲线图",
                "X轴",
                "Y骤",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        ChartFrame frame = new ChartFrame("曲线图", chart);
        frame.setVisible(true);

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                // 生成新的数据点
                double x = Math.random() * 10;
                double y = Math.random() * 10;

                // 添加新的数据点到曲线中
                series.add(x, y);

                // 重新绘制图表
                frame.repaint();
            }
        };

        // 创建一个定时器，按指定的间隔执行定时任务
        Timer timer = new Timer();
        timer.schedule(task, 0, INTERVAL);
    }
}
