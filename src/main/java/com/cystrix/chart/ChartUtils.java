package com.cystrix.chart;


import com.cystrix.hashgraph.exception.BusinessException;
import com.cystrix.hashgraph.hashview.HashgraphMember;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class ChartUtils {
    private static final int INTERVAL = 1000;   // 更新间隔，单位为毫秒
    public static void showTPS(HashgraphMember hashgraphMember, HashgraphMember hashgraphMember2) {
        XYSeries series1 = new XYSeries("Hashgraph TPS");
        XYSeries series2 = new XYSeries("Sharding Hashgraph TPS");

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(series1);
        dataset.addSeries(series2);
        JFreeChart chart = ChartFactory.createXYLineChart(
                "",
                "Time",
                "Transaction Sum",
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
                hashgraphMember2.getHashgraph().forEach((id, chain)->{
                    if (chain.size() != 0) {
                        y2.set( chain.get(chain.size()-1).getEventId()  *  hashgraphMember.getTransactionNum() + y2.get());
                    }
                });

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


    public static void showTPS(HashgraphMember hashgraphMember, HashgraphMember hashgraphMember2, HashgraphMember hashgraphMember3) {
        XYSeries series1 = new XYSeries("Hashgraph");
        XYSeries series2 = new XYSeries("Refer Sharding Hashgraph");
        XYSeries series3 = new XYSeries("Our Sharding Hashgraph");

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(series1);
        dataset.addSeries(series2);
        dataset.addSeries(series3);
        JFreeChart chart = ChartFactory.createXYLineChart(
                "",
                "Time",
                "Transaction Sum",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );
        // 设置曲线的线条粗细
        chart.getXYPlot().getRenderer().setSeriesStroke(0, new java.awt.BasicStroke(2.0f));
        chart.getXYPlot().getRenderer().setSeriesStroke(1, new java.awt.BasicStroke(2.0f));
        chart.getXYPlot().getRenderer().setSeriesStroke(2, new java.awt.BasicStroke(2.0f));

        // 设置曲线上数据点的大小
        chart.getXYPlot().getRenderer().setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-4, -4, 16, 16));
        chart.getXYPlot().getRenderer().setSeriesShape(1, new java.awt.geom.Ellipse2D.Double(-4, -4, 16, 16));
        chart.getXYPlot().getRenderer().setSeriesShape(2, new java.awt.geom.Ellipse2D.Double(-4, -4, 16, 16));
        chart.getXYPlot().getRenderer().setSeriesPaint(0, Color.BLACK);
        chart.getXYPlot().getRenderer().setSeriesPaint(1, Color.BLUE);
        chart.getXYPlot().getRenderer().setSeriesPaint(2, Color.RED);

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
                AtomicInteger y3 = new AtomicInteger(0);
                hashgraphMember.getHashgraph().forEach((id, chain)->{
                    if (chain.size() != 0) {
                        y.set(chain.get(chain.size()-1).getEventId() * hashgraphMember.getTransactionNum() + y.get());
                    }
                });
                hashgraphMember2.getHashgraph().forEach((id, chain)->{
                    if (chain.size() != 0) {
                        y2.set( chain.get(chain.size()-1).getEventId()  *  hashgraphMember.getTransactionNum() + y2.get());
                    }
                });

                hashgraphMember3.getHashgraph().forEach((id, chain)->{
                    if (chain.size() != 0) {
                        y3.set( chain.get(chain.size()-1).getEventId()  *  hashgraphMember.getTransactionNum() + y3.get());
                    }
                });

                // 添加新的数据点到曲线中
                series1.add(x, y);
                series2.add(x, y2);
                series3.add(x, y3);
                // 重新绘制图表
                frame.repaint();
            }
        };

        // 创建一个定时器，按指定的间隔执行定时任务
        Timer timer = new Timer();
        timer.schedule(task, 0, INTERVAL);
    }

    public static void showTPSOne(HashgraphMember hashgraphMember) {
        XYSeries series1 = new XYSeries("Hashgraph TPS");

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(series1);
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

        AtomicInteger before = new AtomicInteger();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                // 生成新的数据点
                double x = time.getAndIncrement();
                AtomicInteger y = new AtomicInteger(0);

                hashgraphMember.getHashgraph().forEach((id, chain)->{
                    if (chain.size() != 0) {
                        y.set(chain.get(chain.size()-1).getEventId() * hashgraphMember.getTransactionNum() + y.get());
                    }
                });
//                if (before.get()> y.get()) {
//                    throw new BusinessException("掉交易量了...");
//                }
//                before.set(y.get());
                // 添加新的数据点到曲线中
                series1.add(x, y);
                // 重新绘制图表
                frame.repaint();
            }
        };

        // 创建一个定时器，按指定的间隔执行定时任务
        Timer timer = new Timer();
        timer.schedule(task, 0, INTERVAL);
    }
}
