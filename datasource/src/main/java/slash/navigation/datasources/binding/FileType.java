//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.7 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2015.03.19 at 12:35:01 PM CET 
//


package slash.navigation.datasources.binding;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * 
 *                 a file is a downloadable which optionally defines a bounding box
 *             
 * 
 * <p>Java class for fileType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="fileType">
 *   &lt;complexContent>
 *     &lt;extension base="{http://api.routeconverter.com/v1/schemas/datasource-catalog}downloadableType">
 *       &lt;sequence>
 *         &lt;element name="boundingBox" type="{http://api.routeconverter.com/v1/schemas/datasource-catalog}boundingBoxType" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "fileType", propOrder = {
    "boundingBox"
})
public class FileType
    extends DownloadableType
{

    protected BoundingBoxType boundingBox;

    /**
     * Gets the value of the boundingBox property.
     * 
     * @return
     *     possible object is
     *     {@link BoundingBoxType }
     *     
     */
    public BoundingBoxType getBoundingBox() {
        return boundingBox;
    }

    /**
     * Sets the value of the boundingBox property.
     * 
     * @param value
     *     allowed object is
     *     {@link BoundingBoxType }
     *     
     */
    public void setBoundingBox(BoundingBoxType value) {
        this.boundingBox = value;
    }

}
