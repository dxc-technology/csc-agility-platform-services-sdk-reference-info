//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vhudson-jaxb-ri-2.1-833 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2015.07.19 at 08:17:35 AM CDT 
//


package com.microsoft.schemas.azure.trafficmgr;

import java.io.Serializable;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for Endpoint complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="Endpoint">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="DomainName" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="Status" type="{http://schemas.microsoft.com/windowsazure}Status"/>
 *         &lt;element name="Type" type="{http://schemas.microsoft.com/windowsazure}Type" minOccurs="0"/>
 *         &lt;element name="MonitorStatus" type="{http://schemas.microsoft.com/windowsazure}MonitorStatus" minOccurs="0"/>
 *         &lt;element name="Location" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="MinChildEndpoints" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *         &lt;element name="Weight" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Endpoint", propOrder = {
    "domainName",
    "status",
    "type",
    "monitorStatus",
    "location",
    "minChildEndpoints",
    "weight"
})
@XmlRootElement(name = "Endpoint")
public class Endpoint
    implements Serializable
{

    private final static long serialVersionUID = 1L;
    @XmlElement(name = "DomainName", required = true)
    protected String domainName;
    @XmlElement(name = "Status", required = true)
    protected Status status;
    @XmlElement(name = "Type")
    protected Type type;
    @XmlElement(name = "MonitorStatus")
    protected MonitorStatus monitorStatus;
    @XmlElement(name = "Location")
    protected String location;
    @XmlElement(name = "MinChildEndpoints")
    protected Integer minChildEndpoints;
    @XmlElement(name = "Weight")
    protected Integer weight;

    /**
     * Gets the value of the domainName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDomainName() {
        return domainName;
    }

    /**
     * Sets the value of the domainName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDomainName(String value) {
        this.domainName = value;
    }

    /**
     * Gets the value of the status property.
     * 
     * @return
     *     possible object is
     *     {@link Status }
     *     
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Sets the value of the status property.
     * 
     * @param value
     *     allowed object is
     *     {@link Status }
     *     
     */
    public void setStatus(Status value) {
        this.status = value;
    }

    /**
     * Gets the value of the type property.
     * 
     * @return
     *     possible object is
     *     {@link Type }
     *     
     */
    public Type getType() {
        return type;
    }

    /**
     * Sets the value of the type property.
     * 
     * @param value
     *     allowed object is
     *     {@link Type }
     *     
     */
    public void setType(Type value) {
        this.type = value;
    }

    /**
     * Gets the value of the monitorStatus property.
     * 
     * @return
     *     possible object is
     *     {@link MonitorStatus }
     *     
     */
    public MonitorStatus getMonitorStatus() {
        return monitorStatus;
    }

    /**
     * Sets the value of the monitorStatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link MonitorStatus }
     *     
     */
    public void setMonitorStatus(MonitorStatus value) {
        this.monitorStatus = value;
    }

    /**
     * Gets the value of the location property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getLocation() {
        return location;
    }

    /**
     * Sets the value of the location property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLocation(String value) {
        this.location = value;
    }

    /**
     * Gets the value of the minChildEndpoints property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getMinChildEndpoints() {
        return minChildEndpoints;
    }

    /**
     * Sets the value of the minChildEndpoints property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setMinChildEndpoints(Integer value) {
        this.minChildEndpoints = value;
    }

    /**
     * Gets the value of the weight property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getWeight() {
        return weight;
    }

    /**
     * Sets the value of the weight property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setWeight(Integer value) {
        this.weight = value;
    }

}
