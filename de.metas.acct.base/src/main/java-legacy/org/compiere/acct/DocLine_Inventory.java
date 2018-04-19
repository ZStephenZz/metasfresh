package org.compiere.acct;

import java.math.BigDecimal;

import org.adempiere.model.InterfaceWrapperHelper;
import org.compiere.Adempiere;
import org.compiere.model.I_C_AcctSchema;
import org.compiere.model.I_M_InventoryLine;
import org.compiere.util.TimeUtil;

import de.metas.costing.CostAmount;
import de.metas.costing.CostDetailCreateRequest;
import de.metas.costing.CostDetailReverseRequest;
import de.metas.costing.CostResult;
import de.metas.costing.CostingDocumentRef;
import de.metas.costing.ICostingService;
import de.metas.quantity.Quantity;

/*
 * #%L
 * de.metas.acct.base
 * %%
 * Copyright (C) 2018 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

public class DocLine_Inventory extends DocLine<Doc_Inventory>
{
	public DocLine_Inventory(final I_M_InventoryLine inventoryLine, final Doc_Inventory doc)
	{
		super(InterfaceWrapperHelper.getPO(inventoryLine), doc);

		final BigDecimal qty;
		BigDecimal qtyInternalUse = inventoryLine.getQtyInternalUse();
		if (qtyInternalUse.signum() != 0)
		{
			qty = qtyInternalUse.negate();		// Internal Use entered positive
		}
		else
		{
			BigDecimal qtyBook = inventoryLine.getQtyBook();
			BigDecimal qtyCount = inventoryLine.getQtyCount();
			qty = qtyCount.subtract(qtyBook);
		}

		setQty(Quantity.of(qty, getProductStockingUOM()), false);

		setReversalLine_ID(inventoryLine.getReversalLine_ID());
	}

	public CostResult getCreateCosts(final I_C_AcctSchema as)
	{
		final ICostingService costDetailService = Adempiere.getBean(ICostingService.class);

		if (isReversalLine())
		{
			return costDetailService.createReversalCostDetails(CostDetailReverseRequest.builder()
					.acctSchemaId(as.getC_AcctSchema_ID())
					.reversalDocumentRef(CostingDocumentRef.ofInventoryLineId(get_ID()))
					.initialDocumentRef(CostingDocumentRef.ofInventoryLineId(getReversalLine_ID()))
					.date(TimeUtil.asLocalDate(getDateDoc()))
					.build());
		}
		else
		{
			return costDetailService.createCostDetail(
					CostDetailCreateRequest.builder()
							.acctSchemaId(as.getC_AcctSchema_ID())
							.clientId(getAD_Client_ID())
							.orgId(getAD_Org_ID())
							.productId(getM_Product_ID())
							.attributeSetInstanceId(getM_AttributeSetInstance_ID())
							.documentRef(CostingDocumentRef.ofInventoryLineId(get_ID()))
							.qty(getQty())
							.amt(CostAmount.zero(as.getC_Currency_ID())) // N/A
							.date(TimeUtil.asLocalDate(getDateDoc()))
							.build());
		}
	}
}
