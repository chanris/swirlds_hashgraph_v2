package com.cystrix.chart;

import com.cystrix.hashgraph.hashview.Event;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ChartUtils {
    private static final int INTERVAL = 1000; // 更新间隔，单位为毫秒
    public static void showTPS(ConcurrentHashMap<Integer, List<Event>> hashgraph, ConcurrentHashMap<Integer, Integer> snapshotHeight) {

        XYSeries series = new XYSeries("Hashgraph TPS Trend Chart");
        series.setMaximumItemCount(10000);

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(series);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "TPS Trend Chart",
                "time",
                "transaction count",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        ChartFrame frame = new ChartFrame("TPS走势图", chart);
        frame.setVisible(true);
        AtomicInteger time = new AtomicInteger(0);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                // 生成新的数据点
                double x = time.getAndIncrement();
                AtomicInteger y = new AtomicInteger(0);
                hashgraph.forEach((id, chain)->{
                    y.set(chain.size() * 10 + y.get() + snapshotHeight.get(id) * 10);
                });

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
