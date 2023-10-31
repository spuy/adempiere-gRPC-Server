package org.spuy.utils;

import com.google.protobuf.ByteString;
import org.adempiere.core.domains.models.I_C_Invoice;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.pos.services.CPOS;
import org.adempiere.pos.util.POSTicketHandler;
import org.compiere.model.MInvoice;
import org.compiere.model.MOrder;
import org.compiere.model.Query;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.openup.LUY.util.CFEPrint;
import org.spin.backend.grpc.common.ReportOutput;
import org.spin.backend.grpc.payment_print_export.ExportResponse;
import org.spin.pos.util.IPrintTicket;
import org.spin.pos.util.TicketHandler;
import org.spin.pos.util.TicketResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class POSPrintTicket implements IPrintTicket {

    protected transient CLogger log = CLogger.getCLogger (getClass());

    private Properties ctx;
    private String transactionName;
    private MInvoice invoice;



    @Override
    public void setHandler(TicketHandler handler) {
        ctx = Env.getCtx();
        transactionName = handler.getTransactionName();
        MOrder order = new MOrder(ctx, handler.getRecordId(), transactionName);
        String whereClause = I_C_Invoice.COLUMNNAME_C_Order_ID + "=?";
        invoice = new Query(ctx, I_C_Invoice.Table_Name, whereClause, transactionName)
                .setParameters(order.get_ID())
                .setOrderBy(I_C_Invoice.COLUMNNAME_Created + " DESC")
                .first();
    }

    @Override
    public TicketResult printTicket() {
        TicketResult ticketResult = TicketResult.newInstance();
        if (invoice != null && invoice.get_ID() > 0) {
            Properties ctx = invoice.getCtx();

            File pdf = CFEPrint.getCFETicket(ctx, transactionName, I_C_Invoice.Table_ID, invoice.get_ID());
            log.info(pdf.toString());

            ticketResult.withReportFile(pdf).withError(false).withSummary("Ok");
        }
        return ticketResult;
    }
}
