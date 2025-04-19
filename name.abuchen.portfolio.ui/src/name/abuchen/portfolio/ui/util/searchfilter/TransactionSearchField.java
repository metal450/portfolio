package name.abuchen.portfolio.ui.util.searchfilter;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.jface.action.ControlContribution;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;

import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.TransactionPair;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.ui.Messages;

public class TransactionSearchField extends ControlContribution
{
    private String filterText;
    private Consumer<String> onRecalculationNeeded;

    public TransactionSearchField(Consumer<String> onRecalculationNeeded)
    {
        super("searchbox"); //$NON-NLS-1$
        this.onRecalculationNeeded = onRecalculationNeeded;
    }

    public String getFilterText()
    {
        return filterText;
    }

    @Override
    protected Control createControl(Composite parent)
    {
        final Text search = new Text(parent, SWT.SEARCH | SWT.ICON_CANCEL);
        search.setMessage(Messages.LabelSearch);
        search.setSize(300, SWT.DEFAULT);

        search.addModifyListener(e -> {
            var text = search.getText().trim();
            if (text.length() == 0)
            {
                filterText = null;
                onRecalculationNeeded.accept(filterText);
            }
            else
            {
                filterText = text.toLowerCase();
                onRecalculationNeeded.accept(filterText);
            }
        });

        return search;
    }

    @Override
    protected int computeWidth(Control control)
    {
        return control.computeSize(100, SWT.DEFAULT, true).x;
    }

    public ViewerFilter getViewerFilter(Function<Object, TransactionPair<?>> transaction)
    {
        List<Function<TransactionPair<?>, Object>> searchLabels = new ArrayList<>();
        searchLabels.add(tx -> tx.getTransaction().getSecurity());
        searchLabels.add(tx -> tx.getTransaction().getOptionalSecurity().map(Security::getIsin).orElse(null));
        searchLabels.add(tx -> tx.getTransaction().getOptionalSecurity().map(Security::getWkn).orElse(null));
        searchLabels.add(tx -> tx.getTransaction().getOptionalSecurity().map(Security::getTickerSymbol).orElse(null));
        searchLabels.add(TransactionPair::getOwner);
        searchLabels.add(tx -> tx.getTransaction().getCrossEntry() != null
                        ? tx.getTransaction().getCrossEntry().getCrossOwner(tx.getTransaction())
                        : null);
        searchLabels.add(tx -> tx.getTransaction() instanceof AccountTransaction
                        ? ((AccountTransaction) tx.getTransaction()).getType()
                        : ((PortfolioTransaction) tx.getTransaction()).getType());
        searchLabels.add(tx -> tx.getTransaction().getNote());
        searchLabels.add(tx -> tx.getTransaction().getShares());
        searchLabels.add(tx -> tx.getTransaction().getMonetaryAmount());

        return new ViewerFilter()
        {
            @Override
            public Object[] filter(Viewer viewer, Object parent, Object[] elements)
            {
                return filterText == null ? elements : super.filter(viewer, parent, elements);
            }

            @Override
            public boolean select(Viewer viewer, Object parentElement, Object element)
            {
                TransactionPair<?> tx = transaction.apply(element);

                for (Function<TransactionPair<?>, Object> label : searchLabels)
                {
                    Object l = label.apply(tx);
                    if (l == null)
                        continue;

                    // If the search text is a number and we're looking at a
                    // numeric field, do a numeric comparison to handle
                    // formatting differences (commas, periods, etc.)
                    Double numericSearch = tryParseAsNumber(filterText);
                    if (numericSearch != null && (l instanceof Money || l instanceof Number))
                    {
                        // Extract the numeric value based on the field type
                        double fieldValue;
                        if (l instanceof Money)
                            fieldValue = ((Money) l).getAmount() / 100.0;
                        else if (l instanceof Long)
                            fieldValue = ((Long) l).doubleValue() / 100000000.0;
                        else
                            fieldValue = ((Number) l).doubleValue();

                        // Check for exact match
                        if (Math.abs(fieldValue - numericSearch) < 0.0001)
                            return true;

                        // For substring matching, convert both to a canonical
                        // string representation using BigDecimal to avoid
                        // locale-specific formatting (BigDecimal always
                        // uses period as the decimal separator)
                        String fieldStr = BigDecimal.valueOf(fieldValue).toPlainString();
                        String searchStr = BigDecimal.valueOf(numericSearch).toPlainString();

                        // Remove trailing ".0" in case of whole numbers (so
                        // i.e. searching "25" won't get stringified to "25.0"
                        // and not match 25.xx as substring)
                        searchStr = searchStr.replace(".0", "");

                        if (fieldStr.contains(searchStr))
                            return true;
                    }

                    // Otherwise use string-based search
                    else if (l.toString().toLowerCase().indexOf(filterText) >= 0)
                        return true;
                }

                return false;
            }
        };
    }

    /**
     * Try to parse the search text as a number using locale-aware parsing.
     * Returns null if the text cannot be parsed as a number.
     */
    private Double tryParseAsNumber(String text)
    {
        try
        {
            return NumberFormat.getInstance().parse(text).doubleValue();
        }
        catch (java.text.ParseException e)
        {
            return null;
        }
    }
}
