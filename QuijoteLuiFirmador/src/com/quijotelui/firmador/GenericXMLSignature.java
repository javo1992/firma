/*
 * Copyright (C) 2014 
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.quijotelui.firmador;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import es.mityc.firmaJava.libreria.utilidades.UtilidadTratarNodo;
import es.mityc.firmaJava.libreria.xades.DataToSign;
import es.mityc.firmaJava.libreria.xades.FirmaXML;

import es.mityc.javasign.pkstore.CertStoreException;
import es.mityc.javasign.pkstore.IPKStoreManager;
import es.mityc.javasign.pkstore.keystore.KSStore;
import java.io.FileInputStream;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;

/**
 *
 * @author jorjoluiso
 */
/* <p>
 * Clase base que deberían extender los diferentes ejemplos para realizar firmas
 * XML.
 * </p>
 * 
 */
public abstract class GenericXMLSignature {

    /**
     * <p>
     * Almacén PKCS12 con el que se desea realizar la firma
     * </p>
     */
    public String PKCS12_RESOURCE = "/examples/usr0061.p12";

    /**
     * <p>
     * Constraseña de acceso a la clave privada del usuario
     * </p>
     */
    public String PKCS12_PASSWORD = "miclave";

    /**
     * <p>
     * Directorio donde se almacenará el resultado de la firma
     * </p>
     */
    public String OUTPUT_DIRECTORY;

    public void setOUTPUT_DIRECTORY(String OUTPUT_DIRECTORY) {
        this.OUTPUT_DIRECTORY = OUTPUT_DIRECTORY;
    }

    /**
     * <p>
     * Ejecución del ejemplo. La ejecución consistirá en la firma de los datos
     * creados por el método abstracto <code>createDataToSign</code> mediante el
     * certificado declarado en la constante <code>PKCS12_FILE</code>. El
     * resultado del proceso de firma será almacenado en un fichero XML en el
     * directorio correspondiente a la constante <code>OUTPUT_DIRECTORY</code>
     * del usuario bajo el nombre devuelto por el método abstracto
     * <code>getSignFileName</code>
     * </p>
     */
    protected boolean execute(TokensAvailables token) {

        try {

            // Obtencion del gestor de claves
            IPKStoreManager storeManager = null;
            String aliaskey = null;
            KeyStore ks = null;
            String store = "pkcs12";

            ks = KeyStore.getInstance(store);
            if (store == "Windows-MY") {
                ks.load(this.getClass().getResourceAsStream(PKCS12_RESOURCE), PKCS12_PASSWORD.toCharArray());
                ks.load(null, null);
                storeManager = new KSStore(ks, new PassStoreKS(PKCS12_PASSWORD));
            } else if (store.equals("pkcs12")) {
                ks.load(new FileInputStream(PKCS12_RESOURCE), PKCS12_PASSWORD.toCharArray());
                //ks.load(this.getClass().getResourceAsStream(PKCS12_RESOURCE), PKCS12_PASSWORD.toCharArray());
                ks.load(null, null);
                storeManager = new KSStore(ks, new PassStoreKS(PKCS12_PASSWORD));
            }

            if (storeManager == null) {
                System.err.println("El gestor de claves no se ha obtenido correctamente.");
                return false;
            }

            aliaskey = selectCertificate(ks, token);

            // Obtencion del certificado para firmar. Utilizaremos el primer
            // certificado del almacen.

            X509Certificate certificate = (X509Certificate) ks.getCertificate(aliaskey);
            if (certificate == null) {
                System.err.println("No existe ningún certificado para firmar");
                return false;
            }

            // Obtención de la clave privada asociada al certificado
            PrivateKey privateKey = null;
            try {
                KeyStore tmpKs = ks;
                privateKey = (PrivateKey) tmpKs.getKey(aliaskey, PKCS12_PASSWORD.toCharArray());
            } catch (UnrecoverableKeyException ex) {
                Logger.getLogger(GenericXMLSignature.class.getName()).log(Level.SEVERE, null, ex);
            }

            // Obtención del provider encargado de las labores criptográficas
            Provider provider = storeManager.getProvider(certificate);

            /*
            * Creación del objeto que contiene tanto los datos a firmar como la
            * configuración del tipo de firma
             */
            DataToSign dataToSign = createDataToSign();

            /*
            * Creación del objeto encargado de realizar la firma
             */
            FirmaXML firma = new FirmaXML();

            // Firmamos el documento
            Document docSigned = null;
            try {
                Object[] res = firma.signFile(certificate, dataToSign, privateKey, provider);
                docSigned = (Document) res[0];
            } catch (Exception ex) {
                System.err.println("Error realizando la firma");
                ex.printStackTrace();
                return false;
            }

            // Guardamos la firma a un fichero en el home del usuario
            String filePath = OUTPUT_DIRECTORY + File.separatorChar + getSignatureFileName();
            System.out.println("Firma salvada en en: " + filePath);
            saveDocumentToFile(docSigned, filePath);
            return true;
        } catch (KeyStoreException ex) {
            Logger.getLogger(GenericXMLSignature.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(GenericXMLSignature.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(GenericXMLSignature.class.getName()).log(Level.SEVERE, null, ex);
        } catch (CertificateNotYetValidException ex) {
            Logger.getLogger(GenericXMLSignature.class.getName()).log(Level.SEVERE, null, ex);
        } catch (CertificateException ex) {
            Logger.getLogger(GenericXMLSignature.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    /**
     * <p>
     * Crea el objeto DataToSign que contiene toda la información de la firma
     * que se desea realizar. Todas las implementaciones deberán proporcionar
     * una implementación de este método
     * </p>
     *
     * @return El objeto DataToSign que contiene toda la información de la firma
     * a realizar
     */
    protected abstract DataToSign createDataToSign();

    /**
     * <p>
     * Nombre del fichero donde se desea guardar la firma generada. Todas las
     * implementaciones deberán proporcionar este nombre.
     * </p>
     *
     * @return El nombre donde se desea guardar la firma generada
     */
    protected abstract String getSignatureFileName();

    /**
     * <p>
     * Escribe el documento a un fichero.
     * </p>
     *
     * @param document El documento a imprmir
     * @param pathfile El path del fichero donde se quiere escribir.
     */
    private void saveDocumentToFile(Document document, String pathfile) {
        try {
            FileOutputStream fos = new FileOutputStream(pathfile);
            UtilidadTratarNodo.saveDocumentToOutputStream(document, fos, true);
        } catch (FileNotFoundException e) {
            System.err.println("Error al salvar el documento");
            e.printStackTrace();
            //System.exit(-1);
        }
    }

    /**
     * <p>
     * Escribe el documento a un fichero. Esta implementacion es insegura ya que
     * dependiendo del gestor de transformadas el contenido podría ser alterado,
     * con lo que el XML escrito no sería correcto desde el punto de vista de
     * validez de la firma.
     * </p>
     *
     * @param document El documento a imprmir
     * @param pathfile El path del fichero donde se quiere escribir.
     */
    @SuppressWarnings("unused")
    private void saveDocumentToFileUnsafeMode(Document document, String pathfile) {
        TransformerFactory tfactory = TransformerFactory.newInstance();
        Transformer serializer;
        try {
            serializer = tfactory.newTransformer();

            serializer.transform(new DOMSource(document), new StreamResult(new File(pathfile)));
        } catch (TransformerException e) {
            System.err.println("Error al salvar el documento");
            e.printStackTrace();
            //System.exit(-1);
        }
    }

    /**
     * <p>
     * Devuelve el <code>Document</code> correspondiente al
     * <code>resource</code> pasado como parámetro
     * </p>
     *
     * @param resource El recurso que se desea obtener
     * @return El <code>Document</code> asociado al <code>resource</code>
     */
    protected Document getDocument(String resource) {
        Document doc = null;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        File fXmlFile;
        fXmlFile = new File(resource);
        dbf.setNamespaceAware(true);
        try {

            DocumentBuilder dBuilder = dbf.newDocumentBuilder();
            doc = dBuilder.parse(fXmlFile);
            /*
             doc = dbf.newDocumentBuilder().parse(
             this.getClass().getResourceAsStream(resource));
             * */
        } catch (ParserConfigurationException ex) {
            System.err.println(GenericXMLSignature.class.getName() + " " + ex.getMessage());
        } catch (SAXException ex) {
            System.err.println(GenericXMLSignature.class.getName() + " " + ex.getMessage());
        } catch (IOException ex) {
            System.err.println(GenericXMLSignature.class.getName() + " " + ex.getMessage());
        }

        return doc;
    }

    /**
     * <p>
     * Devuelve el contenido del documento XML correspondiente al
     * <code>resource</code> pasado como parámetro
     * </p> como un <code>String</code>
     *
     * @param resource El recurso que se desea obtener
     * @return El contenido del documento XML como un <code>String</code>
     */
    protected String getDocumentAsString(String resource) {
        Document doc = getDocument(resource);
        TransformerFactory tfactory = TransformerFactory.newInstance();
        Transformer serializer;
        StringWriter stringWriter = new StringWriter();
        try {
            serializer = tfactory.newTransformer();
            serializer.transform(new DOMSource(doc), new StreamResult(stringWriter));
        } catch (TransformerException e) {
            System.err.println("Error al imprimir el documento");
            e.printStackTrace();
            //System.exit(-1);
        }

        return stringWriter.toString();
    }

    /**
     * <p>
     * Recupera el primero de los certificados del almacén.
     * </p>
     *
     * @param storeManager Interfaz de acceso al almacén
     * @return Primer certificado disponible en el almacén
     */
    private X509Certificate getFirstCertificate(
            final IPKStoreManager storeManager) {
        List<X509Certificate> certs = null;
        try {
            certs = storeManager.getSignCertificates();
        } catch (CertStoreException ex) {
            //System.err.println("Fallo obteniendo listado de certificados");
            System.err.println("Fallo obteniendo listado de certificados");
            return null;
            //System.exit(-1);
        }
        if ((certs == null) || (certs.size() == 0)) {
            //System.err.println("Lista de certificados vacía");
            System.err.println("Lista de certificados vacía");
            return null;
            //System.exit(-1);
        }

        X509Certificate certificate = certs.get(0);
        return certificate;
    }

    public static String selectCertificate(KeyStore keyStore, TokensAvailables tokenSelected) throws KeyStoreException {
        String aliasSeleccion = null;
        X509Certificate certificado = null;
        Enumeration<String> nombres = keyStore.aliases();
        while (nombres.hasMoreElements()) {
            String aliasKey = nombres.nextElement();
            certificado = (X509Certificate) keyStore.getCertificate(aliasKey);

            X500NameGeneral x500emisor = new X500NameGeneral(certificado.getIssuerDN().getName());
            X500NameGeneral x500sujeto = new X500NameGeneral(certificado.getSubjectDN().getName());

            String cn = x500emisor.getCN();
            String a = AutoridadesCertificantes.SECURITY_DATA.getCn();

            Boolean r = cn.contains(a);

            if ((tokenSelected.equals(TokensAvailables.SD_BIOPASS)
                    || tokenSelected.equals(TokensAvailables.SD_EPASS3000))
                    && (x500emisor.getCN().contains(AutoridadesCertificantes.SECURITY_DATA.getCn())
                    || x500emisor.getCN().contains(AutoridadesCertificantes.SECURITY_DATA_SUB_1.getCn()))) {
                         if (AutoridadesCertificantes.SECURITY_DATA.getO().equals(x500emisor.getO())
                                 && AutoridadesCertificantes.SECURITY_DATA.getC().equals(x500emisor.getC())
                                 && AutoridadesCertificantes.SECURITY_DATA.getO().equals(x500sujeto.getO())
                                 && AutoridadesCertificantes.SECURITY_DATA.getC().equals(x500sujeto.getC())) {
                             if (certificado.getKeyUsage()[0]) {
                                     aliasSeleccion = aliasKey;
                                     break;
                                   }
                             }
                         if (AutoridadesCertificantes.SECURITY_DATA_SUB_1.getO().equals(x500emisor.getO())
                                 && AutoridadesCertificantes.SECURITY_DATA_SUB_1.getC().equals(x500emisor.getC())
                                 && AutoridadesCertificantes.SECURITY_DATA_SUB_1.getO().equals(x500sujeto.getO())
                                 && AutoridadesCertificantes.SECURITY_DATA_SUB_1.getC().equals(x500sujeto.getC())) {
                             if (certificado.getKeyUsage()[0]) {
                                 aliasSeleccion = aliasKey;
                                 break;
                             }
                         }
                         continue;
            }
            if (tokenSelected.equals(TokensAvailables.BCE_ALADDIN)
                    || (tokenSelected.equals(TokensAvailables.BCE_IKEY2032)
                    && x500emisor.getCN().contains(AutoridadesCertificantes.BANCO_CENTRAL.getCn()))) {

                if (x500emisor.getO().contains(AutoridadesCertificantes.BANCO_CENTRAL.getO())
                        && AutoridadesCertificantes.BANCO_CENTRAL.getC().equals(x500emisor.getC())
                        && x500sujeto.getO().contains(AutoridadesCertificantes.BANCO_CENTRAL.getO())
                        && AutoridadesCertificantes.BANCO_CENTRAL.getC().equals(x500sujeto.getC())) {

                    if (certificado.getKeyUsage()[0] || certificado.getKeyUsage()[1]) {
                        aliasSeleccion = aliasKey;
                        break;
                    }
                }
                continue;
            }
            if (tokenSelected.equals(TokensAvailables.ANF1)
                    && x500emisor.getCN().contains(AutoridadesCertificantes.ANF.getCn())) {

                if (AutoridadesCertificantes.ANF.getO().equals(x500emisor.getO())
                        && AutoridadesCertificantes.ANF.getC().equals(x500emisor.getC())
                        && AutoridadesCertificantes.ANF.getC().toLowerCase().equals(x500sujeto.getC())) {

                    if (certificado.getKeyUsage()[0] || certificado.getKeyUsage()[1]) {
                        aliasSeleccion = aliasKey;
                        break;
                    }
                }
                continue;
            }
            if (tokenSelected.equals(TokensAvailables.ANF1)
                    && x500emisor.getCN().contains(AutoridadesCertificantes.ANF_ECUADOR_CA1.getCn())) {

                if (AutoridadesCertificantes.ANF_ECUADOR_CA1.getO().equals(x500emisor.getO())
                        && AutoridadesCertificantes.ANF_ECUADOR_CA1.getC().equals(x500emisor.getC())
                        && AutoridadesCertificantes.ANF_ECUADOR_CA1.getC().equals(x500sujeto.getC())) {

                    if (certificado.getKeyUsage()[0] || certificado.getKeyUsage()[1]) {
                        aliasSeleccion = aliasKey;
                        break;
                    }
                }
                continue;
            }
            if (tokenSelected.equals(TokensAvailables.KEY4_CONSEJO_JUDICATURA)
                    && x500emisor.getCN().contains(AutoridadesCertificantes.CONSEJO_JUDICATURA.getCn())) {

                if (x500emisor.getO().contains(AutoridadesCertificantes.CONSEJO_JUDICATURA.getO())
                        && AutoridadesCertificantes.CONSEJO_JUDICATURA.getC().equals(x500emisor.getC())
                        && AutoridadesCertificantes.CONSEJO_JUDICATURA.getC().equals(x500sujeto.getC())) {

                    if (certificado.getKeyUsage()[0] || certificado.getKeyUsage()[1]) {
                        aliasSeleccion = aliasKey;

                        break;
                    }
                }
            }
        }
        return aliasSeleccion;
    }

}
