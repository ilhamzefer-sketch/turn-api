package az.turn.api;

import org.springframework.stereotype.Component;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class AbbPaymentXmlFactory {
    public String create(
            String externalReference,
            long amount,
            String debitAccount,
            String recipientName,
            String recipientAccount,
            String recipientTaxId,
            String recipientBankCode
    ) {
        try {
            StringWriter output = new StringWriter();
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("root");
            xml.writeStartElement("payments");
            xml.writeStartElement("payment");
            element(xml, "type", "IN");
            element(xml, "rrn", externalReference);
            element(xml, "date", LocalDate.now(ZoneId.of("Asia/Baku")).toString());
            element(xml, "account", debitAccount);
            element(xml, "amount", String.valueOf(amount));
            element(xml, "recipientName", recipientName);
            element(xml, "recipientAccount", recipientAccount);
            element(xml, "taxId", recipientTaxId);
            element(xml, "recipientBankCode", recipientBankCode);
            element(xml, "description1", description(externalReference));
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
            return output.toString();
        } catch (XMLStreamException exception) {
            throw new IllegalStateException("ABB ödəniş faylı yaradıla bilmədi.", exception);
        }
    }

    private void element(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        xml.writeStartElement(name);
        xml.writeCharacters(value);
        xml.writeEndElement();
    }

    private String description(String externalReference) {
        String value = "E-Novbe abonelik " + externalReference;
        return value.substring(0, Math.min(value.length(), 35));
    }
}
