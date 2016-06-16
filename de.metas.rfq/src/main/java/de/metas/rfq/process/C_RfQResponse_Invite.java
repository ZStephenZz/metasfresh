package de.metas.rfq.process;

import org.adempiere.util.Services;
import org.compiere.process.SvrProcess;

import de.metas.rfq.IRfqBL;
import de.metas.rfq.model.I_C_RfQResponse;

/*
 * #%L
 * de.metas.business
 * %%
 * Copyright (C) 2016 metas GmbH
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

/**
 * Send Invite/Reminder to Vendor to respond to RfQ.
 *
 * @author metas-dev <dev@metas-fresh.com>
 *
 */
public class C_RfQResponse_Invite extends SvrProcess
{
	// services
	private final transient IRfqBL rfqBL = Services.get(IRfqBL.class);

	@Override
	protected String doIt()
	{
		final I_C_RfQResponse rfqResponse = getRecord(I_C_RfQResponse.class);
		rfqBL.assertDraft(rfqResponse);

		// Send it
		if (rfqBL.sendRfQResponseToVendor(rfqResponse))
		{
			return MSG_OK;
		}

		return MSG_Error;
	}
}
