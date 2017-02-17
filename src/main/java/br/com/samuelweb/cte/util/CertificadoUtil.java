package br.com.samuelweb.cte.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import org.apache.commons.httpclient.protocol.Protocol;

import br.com.samuelweb.cte.Certificado;
import br.com.samuelweb.cte.ConfiguracoesIniciaisCte;
import br.com.samuelweb.cte.exception.CteException;

/**
 * Classe Responsavel Por Carregar os Certificados Do Repositorio do Windows
 * 
 * @author SaMuK
 * 
 */
public class CertificadoUtil {

	private static ConfiguracoesIniciaisCte configuracoesCte;
	private static Date data;

	// Construtor
	public CertificadoUtil() throws CteException {
		configuracoesCte = ConfiguracoesIniciaisCte.getInstance();
	}
	
	public static Certificado certificadoPfx(String caminhoCertificado, String senha) throws CteException{
		
		Certificado certificado = new Certificado();
		try{
			certificado.setArquivo(caminhoCertificado);
			certificado.setSenha(senha);
			
			KeyStore ks = getKeyStore(certificado);
			ks.load(null, null);
	
			Enumeration<String> aliasEnum = ks.aliases();
			String aliasKey = (String) aliasEnum.nextElement();
			certificado.setNome(aliasKey);
			certificado.setTipo(Certificado.ARQUIVO);
			
			Date validade = DataValidade(certificado);
			if(validade == null){
				throw new CteException("Erro ao Pegar Validade do Certificado, Favor verificar Certificado Digital");
			}
			certificado.setVencimento(validade.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
			certificado.setDiasRestantes(diasRestantes(certificado));
			certificado.setValido(valido(certificado));
		}catch (NoSuchAlgorithmException | CertificateException | IOException | KeyStoreException e){
			throw new CteException("Erro ao carregar informações do certificado:"+e.getMessage());
		}
			
		return certificado;
			
	}

	/**
	 * Retorna a Lista de Certificados do Repositorio do Windows
	 * 
	 */ 
	public static List<Certificado> listaCertificadosWindows() throws CteException {

		// Estou setando a variavel para 20 dispositivos no maximo
		List<Certificado> listaCert = new ArrayList<>(20);

		try {
			KeyStore ks = KeyStore.getInstance("Windows-MY", "SunMSCAPI");

			ks.load(null, null);

			Enumeration<String> aliasEnum = ks.aliases();

			while (aliasEnum.hasMoreElements()) {
				String aliasKey = (String) aliasEnum.nextElement();

				if (ObjetoUtil.differentNull(aliasKey)) {
					Certificado cert = new Certificado();
					cert.setNome(aliasKey);
					cert.setTipo(Certificado.WINDOWS);
					cert.setSenha("");
					Date dataValidade = DataValidade(cert);
					if(dataValidade == null){
						cert.setNome("(INVÁLIDO)"+aliasKey);
						cert.setVencimento(LocalDate.of(2000, 1, 1));
						cert.setDiasRestantes(0L);
						cert.setValido(false);
					}else{
						cert.setVencimento(dataValidade.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
						cert.setDiasRestantes(diasRestantes(cert)); 
						cert.setValido(valido(cert));
					}
					
					listaCert.add(cert);
				}

			}

		} catch (KeyStoreException | NoSuchProviderException | NoSuchAlgorithmException | CertificateException
				| IOException ex) {
			throw new CteException("Erro ao Carregar Certificados:" + ex.getMessage());
		}

		return listaCert;

	}

	// Procedimento que retorna a Data De Validade Do Certificado Digital

	private static Date DataValidade(Certificado certificado) throws CteException {
		
		if(data != null){
			return data;
		}

		try {
			X509Certificate cert = null;
			KeyStore.PrivateKeyEntry pkEntry = null;
			KeyStore ks = null;
			if(certificado.getTipo().equals(Certificado.WINDOWS)){
				ks = KeyStore.getInstance("Windows-MY", "SunMSCAPI");
			}else if(certificado.getTipo().equals(Certificado.ARQUIVO)){
				ks = getKeyStore(certificado);
			}
			
			ks.load(null, null);
			if (ks.isKeyEntry(certificado.getNome())) {
				pkEntry = (KeyStore.PrivateKeyEntry) ks.getEntry(certificado.getNome(), new KeyStore.PasswordProtection(certificado.getSenha().toCharArray()));
			}else{
				return null;
			}
	
			cert = (X509Certificate) pkEntry.getCertificate();
	
			if (cert == null) {
				return null;
			}
			data = cert.getNotAfter();
		} catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableEntryException | NoSuchProviderException | CertificateException | IOException e) {
			throw new CteException("Erro ao Pegar Data Certificado:"+e.getMessage());
		}
		
		return data;

	}

	// Retorna Os dias Restantes do Certificado Digital
	private static Long diasRestantes(Certificado certificado) throws CteException {

		Date data = DataValidade(certificado);
		if (data == null) {
			return null;
		}
		long differenceMilliSeconds = data.getTime() - new Date().getTime();
		return differenceMilliSeconds / 1000 / 60 / 60 / 24;
	}

	// retorna True se o Certificado for validao
	public static boolean valido(Certificado certificado) throws CteException {

		if (DataValidade(certificado) != null && DataValidade(certificado).after(new Date())) {
			return true;
		} else {
			return false;
		}

	}

	@SuppressWarnings("restriction")
	public void iniciaConfiguracoes() throws CteException {

		System.setProperty("sun.security.ssl.allowUnsafeRenegotiation", "true");
		System.setProperty("java.protocol.handler.pkgs", "com.sun.net.ssl.internal.www.protocol");
		Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());

		System.clearProperty("javax.net.ssl.keyStore");
		System.clearProperty("javax.net.ssl.keyStorePassword");
		System.clearProperty("javax.net.ssl.trustStore");
		
		if(configuracoesCte.getProxy()!=null){
			System.setProperty("http.proxyHost", configuracoesCte.getProxy().getProxyHostName());
			System.setProperty("http.proxyPort", configuracoesCte.getProxy().getProxyPort());
			System.setProperty("http.proxyUser", configuracoesCte.getProxy().getProxyUserName()); 
			System.setProperty("http.proxyPassword", configuracoesCte.getProxy().getProxyPassWord()); 
		}

		System.setProperty("jdk.tls.client.protocols", "TLSv1"); // Servidor do	Sefaz RS

		if(configuracoesCte.getCertificado().getTipo().equals(Certificado.WINDOWS)){
			System.setProperty("javax.net.ssl.keyStoreProvider", "SunMSCAPI");
			System.setProperty("javax.net.ssl.keyStoreType", "Windows-MY");
			System.setProperty("javax.net.ssl.keyStoreAlias", configuracoesCte.getCertificado().getNome());
		}else if(configuracoesCte.getCertificado().getTipo().equals(Certificado.ARQUIVO)){
			System.setProperty("javax.net.ssl.keyStoreType", "PKCS12");  
			System.setProperty("javax.net.ssl.keyStore", configuracoesCte.getCertificado().getArquivo());  
		}

		System.setProperty("javax.net.ssl.keyStorePassword", configuracoesCte.getCertificado().getSenha());

		System.setProperty("javax.net.ssl.trustStoreType", "JKS");
		
		//Extrair Cacert do Jar
		String cacert = "";
        try {
            InputStream input = getClass().getResourceAsStream("/Cacert");
            File file = File.createTempFile("tempfile", ".tmp");
            OutputStream out = new FileOutputStream(file);
            int read;
            byte[] bytes = new byte[1024];

            while ((read = input.read(bytes)) != -1) {
                out.write(bytes, 0, read);
            }
            out.close();
            cacert = file.getAbsolutePath();
            file.deleteOnExit();
        } catch (IOException ex) {
            throw new CteException(ex.getMessage());
        }
	   
		System.setProperty("javax.net.ssl.trustStore", cacert);
		
		if(configuracoesCte.isProtocol()){
			try {
				System.out.println("Modo Protocol Ativado.");
				ativaProtocolo(configuracoesCte.getCertificado());
			} catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException | NoSuchProviderException | CertificateException | IOException e) {
				 throw new CteException(e.getMessage());
			}
		}
		
	}

	public static KeyStore getKeyStore(Certificado certificado) throws CteException{
		try {
	        File file = new File(certificado.getArquivo());
	        if(!file.exists()){
				throw new CteException("Certificado Digital não Encontrado");
	        }
	        
			KeyStore keyStore = KeyStore.getInstance("PKCS12");
			keyStore.load(new ByteArrayInputStream(getBytesFromInputStream(new FileInputStream(file))), certificado.getSenha().toCharArray());
			return keyStore;
		} catch (NoSuchAlgorithmException | CertificateException | IOException | KeyStoreException e) {
			throw new CteException("Erro Ao Ler Certificado: "+e.getMessage());
		}
		
	}
	
	public static byte[] getBytesFromInputStream(InputStream is) throws IOException
	{
	    try (ByteArrayOutputStream os = new ByteArrayOutputStream();)
	    {
	        byte[] buffer = new byte[0xFFFF];

	        for (int len; (len = is.read(buffer)) != -1;)
	            os.write(buffer, 0, len);

	        os.flush();

	        return os.toByteArray();
	    }
	}
	
	public void ativaProtocolo(Certificado certificado) throws CteException, KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException, NoSuchProviderException, CertificateException, IOException{
		
		KeyStore ks = null ;
		
		if(certificado.getTipo().equals(Certificado.WINDOWS)){
			ks = KeyStore.getInstance("Windows-MY", "SunMSCAPI");
			ks.load(null, null);
		}else if(certificado.getTipo().equals(Certificado.ARQUIVO)){
			ks = getKeyStore(certificado);
		}
		
		X509Certificate certificate = (X509Certificate) ks.getCertificate(certificado.getNome());  
        PrivateKey privateKey = (PrivateKey) ks.getKey(certificado.getNome(), certificado.getSenha().toCharArray());  
		SocketFactoryDinamico socketFactory = new SocketFactoryDinamico(certificate, privateKey );
		socketFactory.setFileCacerts(getClass().getResourceAsStream("/Cacert"));
		Protocol protocol = new Protocol("https", socketFactory, 443);
		Protocol.registerProtocol("https", protocol);
	}

}