package com.cystrix.chart;


import com.cystrix.hashgraph.hashview.HashgraphMember;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class ChartUtils {
    private static final int INTERVAL = 1000; // 更新间隔，单位为毫秒
    public static void showTPS(HashgraphMember hashgraphMember) {
        XYSeries series1 = new XYSeries("Hashgraph TPS");
        XYSeries series2 = new XYSeries("Sharding Hashgraph TPS");

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(series1);
        dataset.addSeries(series2);
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Hashgraph",
                "Time",
                "TPS",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );
        // 设置曲线的线条粗细
        chart.getXYPlot().getRenderer().setSeriesStroke(0, new java.awt.BasicStroke(2.0f));

        // 设置曲线上数据点的大小
        chart.getXYPlot().getRenderer().setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-4, -4, 16, 16));

        ChartFrame frame = new ChartFrame("", chart);
        frame.setVisible(true);
        AtomicInteger time = new AtomicInteger(0);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                // 生成新的数据点

                double x = time.getAndIncrement();
                AtomicInteger y = new AtomicInteger(0);
                AtomicInteger y2 = new AtomicInteger(0);
                hashgraphMember.getHashgraph().forEach((id, chain)->{
                    //y.set(chain.size() * 10 + y.get() + hashgraphMember.getSnapshotHeightMap().get(id) * 10);
                    if (chain.size() != 0) {
                        y.set(chain.get(chain.size()-1).getEventId() * hashgraphMember.getTransactionNum() + y.get());

                    }

                });
                //y2.set(hashgraphMember.getConsensusEventNum() * 10);
                // 添加新的数据点到曲线中
                series1.add(x, y);
                series2.add(x, y2);
                // 重新绘制图表
                frame.repaint();
            }
        };

        // 创建一个定时器，按指定的间隔执行定时任务
        Timer timer = new Timer();
        timer.schedule(task, 0, INTERVAL);
    }
}
