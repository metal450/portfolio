package name.abuchen.portfolio.ui.views.taxonomy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swtchart.ICircularSeries;
import org.eclipse.swtchart.ISeries.SeriesType;
import org.eclipse.swtchart.model.Node;

import name.abuchen.portfolio.model.Classification.Assignment;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.util.Colors;
import name.abuchen.portfolio.ui.util.chart.CircularChart;
import name.abuchen.portfolio.ui.util.chart.CircularChartToolTip;
import name.abuchen.portfolio.ui.views.taxonomy.TaxonomyNode.AssignmentNode;
import name.abuchen.portfolio.util.ColorConversion;
import name.abuchen.portfolio.util.Pair;

public class DonutChartBuilder
{
    public final void configureToolTip(CircularChartToolTip toolTip)
    {
        toolTip.setToolTipBuilder(new TaxonomyPieChartSWT.TaxonomyTooltipBuilder());
    }

    public final void createCircularSeries(CircularChart chart, TaxonomyModel model)
    {
        ICircularSeries<?> circularSeries = (ICircularSeries<?>) chart.getSeriesSet().createSeries(SeriesType.DOUGHNUT,
                        model.getTaxonomy().getName());

        circularSeries.setSliceColor(chart.getPlotArea().getBackground());

        Node rootNode = circularSeries.getRootNode();
        rootNode.setData(model.getChartRenderingRootNode());

        Map<String, Color> id2color = new HashMap<>();

        computeNodeList(model).forEach(pair -> {
            // create a new identifier because an investment vehicle can be
            // assigned to multiple classifications
            String id = pair.getLeft().getId() + pair.getRight().getId();

            Node childNode = rootNode.addChild(id, getMoney(pair.getRight()).getAmount() / Values.Amount.divider());
            childNode.setData(pair.getRight());

            Color color = Colors.getColor(ColorConversion.hex2RGB(pair.getLeft().getColor()));
            id2color.put(id, color);
        });

        if (id2color.isEmpty())
        {
            circularSeries.setSeries(new String[] { Messages.LabelErrorNoHoldings }, new double[] { 100 });
            circularSeries.setColor(Messages.LabelErrorNoHoldings, Colors.LIGHT_GRAY);
        }

        id2color.forEach(circularSeries::setColor);
    }

    /**
     * Computes the list of nodes to be displayed. If investment vehicles are
     * assigned to multiple nodes, then the node is merged. The left side of the
     * pair contains the relevant parent (top-level classification); the right
     * side the child node.
     */
    public final List<Pair<TaxonomyNode, TaxonomyNode>> computeNodeList(TaxonomyModel model)
    {
        List<Pair<TaxonomyNode, TaxonomyNode>> answer = new ArrayList<>();

        // classified nodes
        TaxonomyNode node = model.getClassificationRootNode();
        addChildren(answer, node, node.getChildren(), model.isSecuritiesInPieChartExcluded());

        // add unclassified if included
        if (!model.isUnassignedCategoryInChartsExcluded())
        {
            TaxonomyNode unassigned = model.getUnassignedNode();
            if (model.isSecuritiesInPieChartExcluded())
            {
                answer.add(new Pair<>(unassigned, unassigned));
            }
            else
            {
                List<TaxonomyNode> children = new ArrayList<>(unassigned.getChildren());
                Collections.sort(children, (r, l) -> getMoney(l).compareTo(getMoney(r)));
                addChildren(answer, unassigned, children, model.isSecuritiesInPieChartExcluded());
            }
        }

        return answer;
    }

    private void addChildren(List<Pair<TaxonomyNode, TaxonomyNode>> answer, TaxonomyNode parent,
                    List<TaxonomyNode> children, boolean firstLevelOnly)
    {
        for (TaxonomyNode child : children)
        {
            if (getMoney(child).isZero())
                continue;

            if (child.isAssignment())
            {
                answer.add(new Pair<>(parent, child));
            }
            else if (child.isClassification() && firstLevelOnly)
            {
                answer.add(new Pair<>(child, child));
            }
            else if (child.isClassification())
            {
                List<TaxonomyNode> grandchildren = new ArrayList<>();
                child.accept(n -> {
                    if (n.isAssignment())
                        grandchildren.add(n);
                });

                // merge investment vehicles that have been assigned to multiple
                // sub-node into one assignment
                grandchildren.stream() //
                                .filter(n -> !getMoney(n).isZero())
                                .collect(Collectors.toMap(TaxonomyNode::getBackingInvestmentVehicle, n -> n, (r, l) -> {

                                    Assignment mergedAssignment = new Assignment(r.getBackingInvestmentVehicle(),
                                                    r.getAssignment().getWeight() + r.getAssignment().getWeight());

                                    TaxonomyNode mergedNode = new AssignmentNode(child, mergedAssignment);
                                    mergedNode.setActual(r.getActual().add(l.getActual()));

                                    // no need to merge targets as they are
                                    // always null for assignments

                                    return mergedNode;
                                })) //
                                .values().stream() //
                                .sorted((l, r) -> getMoney(l).compareTo(getMoney(r))) //
                                .forEach(grandchild -> answer.add(new Pair<>(child, grandchild)));
            }
        }
    }

    protected Money getMoney(TaxonomyNode node)
    {
        return node.getActual();
    }
}
