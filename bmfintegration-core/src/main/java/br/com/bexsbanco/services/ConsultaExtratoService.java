package br.com.bexsbanco.services;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import br.com.bexsbanco.converters.consulta.lote.xml.ConsultaExtratoXmlConverter;
import br.com.bexsbanco.dao.ErrorMessageDAO;
import br.com.bexsbanco.dao.ExtratoTransacaoDAO;
import br.com.bexsbanco.enums.UrlKeys;
import br.com.bexsbanco.logs.BexBancoLogger;
import br.com.bexsbanco.pojos.DocPojo;
import br.com.bexsbanco.pojos.consulta.ErrorMessage;
import br.com.bexsbanco.pojos.consulta.extrato.ConsultaExtratoResponse;
import br.com.bexsbanco.pojos.consulta.extrato.Movimento;
import br.com.bexsbanco.scheduler.BmfScheduler;
import br.com.bexsbanco.util.DllUtils;
import br.com.bexsbanco.util.NumberUtils;
import br.com.bexsbanco.util.PropertiesUtil;
import br.com.bexsbanco.util.WebServiceUtils;

public class ConsultaExtratoService {

	public String consultaExtratoSimulador(Movimento movimento) {
		Integer idLogger = NumberUtils.randomId();
		String xmlResult = "";
		try {

			String xml = ConsultaExtratoXmlConverter.toXML(NumberUtils
					.randomId().toString(), movimento);

			BexBancoLogger.loggerInfo("[" + idLogger
					+ "]XML para envio sem security:" + xml);

			String assinaBmf = DllUtils.assinaBmf("BBMFConsMovtoConta", xml,
					idLogger);

			BexBancoLogger.loggerInfo("[" + idLogger
					+ "]XML para envio com security:" + assinaBmf);

			if (assinaBmf != null) {
				xmlResult = WebServiceUtils.send(UrlKeys.XML.getDesc(),
						assinaBmf);

				BexBancoLogger.loggerInfo("[" + idLogger + "]XML de resposta:"
						+ xmlResult);
			}

		} catch (Exception e) {
			BexBancoLogger.loggerError("[" + idLogger
					+ "]Ocorreu algum erro na consulta de extrato :"
					+ e.getMessage());
			return "Ocorreu algum erro na consulta";
		}

		return xmlResult;

	}

	public boolean consultaExtrato() {

		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");

		boolean result = true;

		try {

			String diasAnteriores = PropertiesUtil
					.getValor("bexsbanco_consulta_extrato_dias_anteriores");

			int qtdDias = Integer.parseInt(diasAnteriores);

			Calendar cal = Calendar.getInstance();

			String dataAtual = dateFormat.format(cal.getTime());

			String dataFormulario = PropertiesUtil
					.getValor("bexsbanco_consulta_extrato_data_lancamento");

			if (dataFormulario == null
					|| dataFormulario.trim().equalsIgnoreCase("")) {

				result = consultaExtratoComData(dataAtual);

				while (qtdDias > 0) {

					cal = Calendar.getInstance();

					cal.add(Calendar.DATE, -qtdDias);
					String dataAnterior = dateFormat.format(cal.getTime());
					result = consultaExtratoComData(dataAnterior);

					qtdDias--;
				}

			} else {

				result = consultaExtratoComData(dataFormulario);

			}

		} catch (Exception e) {
			result = false;
		}

		return result;

	}

	private boolean consultaExtratoComData(String data) {

		boolean result = true;

		Integer idLogger = NumberUtils.randomId();
		String xmlResult = "";
		try {

			Movimento movimento = new Movimento();
			movimento.setAgencia(PropertiesUtil
					.getValor("bexsbanco_consulta_extrato_agencia"));
			movimento.setConta(PropertiesUtil
					.getValor("bexsbanco_consulta_extrato_conta"));
			movimento.setTipo(PropertiesUtil
					.getValor("bexsbanco_consulta_extrato_tipo"));
			movimento.setDtLancamento(data);
			movimento.setNumMotivo(PropertiesUtil
					.getValor("bexsbanco_consulta_extrato_movimento"));

			String xml = ConsultaExtratoXmlConverter.toXML(NumberUtils
					.randomId().toString(), movimento);

			BexBancoLogger.loggerInfo("[" + idLogger
					+ "]XML para envio sem security:" + xml);

			String assinaBmf = DllUtils.assinaBmf("BBMFConsMovtoConta", xml,
					idLogger);

			BexBancoLogger.loggerInfo("[" + idLogger
					+ "]XML para envio com security:" + assinaBmf);

			if (assinaBmf != null) {
				xmlResult = WebServiceUtils.send(UrlKeys.XML.getDesc(),
						assinaBmf);

				BexBancoLogger.loggerInfo("[" + idLogger + "]XML de resposta:"
						+ xmlResult);

				DocPojo responseObject = ConsultaExtratoXmlConverter
						.fromXML(xmlResult);

				ExtratoTransacaoDAO dao = new ExtratoTransacaoDAO();
				ErrorMessageDAO errorMessageDAO = new ErrorMessageDAO();
				if (responseObject != null
						&& responseObject.getSisMsg() != null
						&& responseObject.getSisMsg().getBcMasg() != null
						&& responseObject.getSisMsg().getBcMasg()
								.getConsultaExtratoResponse() != null) {

					ConsultaExtratoResponse consultaExtratoResponse = responseObject
							.getSisMsg().getBcMasg()
							.getConsultaExtratoResponse();

					dao.saveLoteTransacao(
							consultaExtratoResponse.getContabmf(),
							consultaExtratoResponse.getMovimento());
					BexBancoLogger.loggerInfo("[" + idLogger
							+ "] Extrato salvo com sucesso");
				} else if ((responseObject != null
						&& responseObject.getSisMsg() != null
						&& responseObject.getSisMsg().getBcMasg() != null && responseObject
						.getSisMsg().getBcMasg().getErrorMessage() != null)
						||

						(responseObject != null
								&& responseObject.getSisMsg() != null && responseObject
								.getSisMsg().getErrorMessage() != null)

						|| (responseObject != null
								&& responseObject.getBcMasg() != null && responseObject
								.getBcMasg().getErrorMessage() != null)) {

					ErrorMessage errorMessage = null;
					
					if( responseObject.getSisMsg() != null ) {
						
						errorMessage = responseObject.getSisMsg().getErrorMessage() != null ? responseObject
						.getSisMsg().getErrorMessage() : responseObject.getSisMsg().getBcMasg()
										.getErrorMessage();
					} else if( responseObject.getBcMasg() != null ){
						
						errorMessage = responseObject.getBcMasg().getErrorMessage() != null ? responseObject
								.getBcMasg().getErrorMessage()
								: null;
					}
					
					errorMessageDAO.saveErrorMessage("ConsultaExtrato",
							errorMessage);
					BexBancoLogger
							.loggerInfo("["
									+ idLogger
									+ "] BMF retornou um erro, porfavor verificar errorMessage");

					if (errorMessage.getErrorNumber()
							.equalsIgnoreCase("-70010")) {
						BexBancoLogger
								.loggerInfo("["
										+ idLogger
										+ "] Servico foi paradado devido ao erro grave :"+errorMessage.getDescription());
						BmfScheduler.shutdownScheduler();
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			BexBancoLogger.loggerError("[" + idLogger
					+ "]Ocorreu algum erro na consulta de extrato :"
					+ e.getMessage());

			result = false;
		}
		return result;
	}
}
