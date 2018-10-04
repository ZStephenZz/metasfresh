package de.metas.inoutcandidate.api.impl;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.adempiere.ad.dao.IQueryBL;
import org.adempiere.ad.dao.IQueryBuilder;
import org.adempiere.ad.dao.impl.DateTruncQueryFilterModifier;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.mm.attributes.AttributeSetInstanceId;
import org.adempiere.uom.api.IUOMDAO;
import org.adempiere.warehouse.WarehouseId;
import org.adempiere.warehouse.WarehouseTypeId;
import org.compiere.model.IQuery;
import org.compiere.model.I_C_UOM;
import org.compiere.util.TimeUtil;

import com.google.common.collect.ImmutableList;

import de.metas.bpartner.BPartnerId;
import de.metas.bpartner.BPartnerLocationId;
import de.metas.inoutcandidate.api.IPackagingDAO;
import de.metas.inoutcandidate.api.Packageable;
import de.metas.inoutcandidate.api.Packageable.PackageableBuilder;
import de.metas.inoutcandidate.api.PackageableQuery;
import de.metas.inoutcandidate.api.ShipmentScheduleId;
import de.metas.inoutcandidate.model.I_M_Packageable_V;
import de.metas.money.CurrencyId;
import de.metas.money.Money;
import de.metas.order.OrderId;
import de.metas.order.OrderLineId;
import de.metas.product.ProductId;
import de.metas.quantity.Quantity;
import de.metas.shipping.ShipperId;
import de.metas.util.Services;
import lombok.NonNull;

public class PackagingDAO implements IPackagingDAO
{
	private final IUOMDAO uomsRepo = Services.get(IUOMDAO.class);

	@Override
	public List<Packageable> retrievePackableLines(final PackageableQuery query)
	{
		return createQuery(query)
				.stream(I_M_Packageable_V.class)
				.map(this::toPackageable)
				.collect(ImmutableList.toImmutableList());
	}

	private IQuery<I_M_Packageable_V> createQuery(final PackageableQuery query)
	{
		final IQueryBuilder<I_M_Packageable_V> queryBuilder = Services.get(IQueryBL.class)
				.createQueryBuilder(I_M_Packageable_V.class)
				.orderBy()
				.addColumn(I_M_Packageable_V.COLUMN_ProductName)
				.addColumn(I_M_Packageable_V.COLUMN_PriorityRule)
				.addColumn(I_M_Packageable_V.COLUMN_DateOrdered)
				.endOrderBy();

		//
		// Filter: M_Warehouse_ID
		queryBuilder.addEqualsFilter(I_M_Packageable_V.COLUMN_M_Warehouse_ID, query.getWarehouseId());

		//
		// Filter: DeliveryDate
		if (query.getDeliveryDate() != null)
		{
			queryBuilder.addCompositeQueryFilter()
					.setJoinOr()
					.addEqualsFilter(I_M_Packageable_V.COLUMN_DeliveryDate, query.getDeliveryDate(), DateTruncQueryFilterModifier.DAY)
					.addEqualsFilter(I_M_Packageable_V.COLUMN_DeliveryDate, null);
		}

		return queryBuilder.create();
	}

	@Override
	public Packageable getByShipmentScheduleId(@NonNull final ShipmentScheduleId shipmentScheduleId)
	{
		final I_M_Packageable_V record = retrievePackageableRecordByShipmentScheduleId(shipmentScheduleId);
		if (record == null)
		{
			throw new AdempiereException("@NotFound@ @M_Packageable_V@ (@M_ShipmentSchedule_ID@=" + shipmentScheduleId + ")");
		}
		return toPackageable(record);
	}

	@Override
	public List<Packageable> getByShipmentScheduleIds(@NonNull final Collection<ShipmentScheduleId> shipmentScheduleIds)
	{
		if (shipmentScheduleIds.isEmpty())
		{
			return ImmutableList.of();
		}

		return retrievePackageableRecordsByShipmentScheduleIds(shipmentScheduleIds)
				.stream()
				.map(this::toPackageable)
				.collect(ImmutableList.toImmutableList());
	}

	@Override
	public Stream<Packageable> streamAll()
	{
		return Services.get(IQueryBL.class)
				.createQueryBuilder(I_M_Packageable_V.class)
				.create()
				.stream(I_M_Packageable_V.class)
				.map(this::toPackageable);
	}

	private Packageable toPackageable(@NonNull final I_M_Packageable_V record)
	{
		final BPartnerId bpartnerId = BPartnerId.ofRepoId(record.getC_BPartner_Customer_ID());
		final I_C_UOM uom = uomsRepo.getById(record.getC_UOM_ID());

		final PackageableBuilder packageable = Packageable.builder();
		packageable.customerId(bpartnerId);
		packageable.customerBPValue(record.getBPartnerValue());
		packageable.customerName(record.getBPartnerName());
		packageable.customerLocationId(BPartnerLocationId.ofRepoId(bpartnerId, record.getC_BPartner_Location_ID()));
		packageable.customerBPLocationName(record.getBPartnerLocationName());
		packageable.customerAddress(record.getBPartnerAddress_Override());

		packageable.qtyOrdered(Quantity.of(record.getQtyOrdered(), uom));
		packageable.qtyToDeliver(Quantity.of(record.getQtyToDeliver(), uom));
		packageable.qtyDelivered(Quantity.of(record.getQtyDelivered(), uom));
		packageable.qtyPicked(Quantity.of(record.getQtyPicked(), uom));
		packageable.qtyPickedPlanned(Quantity.of(record.getQtyPickedPlanned(), uom));

		packageable.warehouseId(WarehouseId.ofRepoId(record.getM_Warehouse_ID()));
		packageable.warehouseName(record.getWarehouseName());
		packageable.warehouseTypeId(WarehouseTypeId.ofRepoIdOrNull(record.getM_Warehouse_Type_ID()));

		packageable.productId(ProductId.ofRepoId(record.getM_Product_ID()));
		packageable.productName(record.getProductName());
		packageable.asiId(AttributeSetInstanceId.ofRepoIdOrNone(record.getM_AttributeSetInstance_ID()));

		packageable.deliveryVia(record.getDeliveryViaRule());

		packageable.shipperId(ShipperId.ofRepoIdOrNull(record.getM_Shipper_ID()));
		packageable.shipperName(record.getShipperName());

		packageable.deliveryDate(TimeUtil.asLocalDateTime(record.getDeliveryDate())); // 01676
		packageable.preparationDate(TimeUtil.asLocalDateTime(record.getPreparationDate()));

		packageable.shipmentScheduleId(ShipmentScheduleId.ofRepoId(record.getM_ShipmentSchedule_ID()));

		packageable.displayed(record.isDisplayed());

		packageable.salesOrderId(OrderId.ofRepoIdOrNull(record.getC_OrderSO_ID()));
		packageable.salesOrderDocumentNo(record.getOrderDocumentNo());
		packageable.salesOrderDocSubType(record.getDocSubType());

		packageable.salesOrderLineIdOrNull(OrderLineId.ofRepoIdOrNull(record.getC_OrderLineSO_ID()));

		final CurrencyId currencyId = CurrencyId.ofRepoIdOrNull(record.getC_Currency_ID());
		if (currencyId != null)
		{
			packageable.salesOrderLineNetAmt(Money.of(record.getLineNetAmt(), currencyId));
		}

		packageable.freightCostRule(record.getFreightCostRule());

		return packageable.build();
	}

	@Override
	public BigDecimal retrieveQtyPickedPlannedOrNull(@NonNull final ShipmentScheduleId shipmentScheduleId)
	{
		final I_M_Packageable_V record = retrievePackageableRecordByShipmentScheduleId(shipmentScheduleId);
		if (record == null)
		{
			return null;
		}
		return record.getQtyPickedPlanned();

	}

	private List<I_M_Packageable_V> retrievePackageableRecordsByShipmentScheduleIds(@NonNull final Collection<ShipmentScheduleId> shipmentScheduleIds)
	{
		if (shipmentScheduleIds.isEmpty())
		{
			return ImmutableList.of();
		}

		return Services.get(IQueryBL.class)
				.createQueryBuilder(I_M_Packageable_V.class)
				.addInArrayFilter(I_M_Packageable_V.COLUMN_M_ShipmentSchedule_ID, shipmentScheduleIds)
				.create()
				.list(I_M_Packageable_V.class);
	}

	private I_M_Packageable_V retrievePackageableRecordByShipmentScheduleId(final ShipmentScheduleId shipmentScheduleId)
	{
		return Services.get(IQueryBL.class)
				.createQueryBuilder(I_M_Packageable_V.class)
				.addEqualsFilter(I_M_Packageable_V.COLUMN_M_ShipmentSchedule_ID, shipmentScheduleId)
				.create()
				.firstOnly(I_M_Packageable_V.class);
	}
}
