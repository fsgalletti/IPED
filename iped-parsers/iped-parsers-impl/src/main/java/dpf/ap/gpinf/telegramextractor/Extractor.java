package dpf.ap.gpinf.telegramextractor;
import java.io.File;

import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import dpf.ap.gpinf.InterfaceTelegram.DecoderTelegramInterface;
import dpf.ap.gpinf.InterfaceTelegram.PhotoData;
import iped3.io.IItemBase;

import iped3.search.IItemSearcher;
import iped3.util.BasicProps;
public class Extractor {
	Connection conn;
	public Extractor(Connection conn) {
		this.conn=conn;
	}
    private File databaseFile;
	

    private ArrayList<Chat> chatList=null;

    private HashMap<Long, Contact> contacts = new HashMap<>();
    
    
    void performExtraction() {
        try {
        	
            if(conn==null) {
            	conn=getConnection();
            }
            extractContacts();
            chatList = extractChatList();
        } catch (Exception e ) {
            //log de erro
        }
    }
   
    protected Contact getContact(long id) {
    	if(contacts.get(id)!=null) {
    		return contacts.get(id); 
    	}else {
    		Contact c=new Contact(id);
    		contacts.put(id, c);
    		return c;
    	}
    	
    }
    protected  ArrayList<Chat> extractChatList(){
    	 ArrayList<Chat> l=new ArrayList<>();
    	 System.out.println("parser telegram!!!!!");
     	try {
     		DecoderTelegramInterface d=(DecoderTelegramInterface)Class.forName(DECODER_CLASS).newInstance();
             PreparedStatement stmt = conn.prepareStatement(CHATS_SQL);
             ResultSet rs = stmt.executeQuery();
             while (rs.next()) {
            	 long chatId = rs.getLong("chatId");
            	 byte[] dados;
                 Chat cg=null;
                 String chatName = null;
                 if ((chatName=rs.getString("nomeChat")) != null) {
                	 dados = rs.getBytes("dadosChat");
                	 Contact user=new Contact(0);
                	 d.setDecoderData(dados, DecoderTelegramInterface.USER);
                	 d.getUserData(user);
                     
                     
                     
                     if (user.getId()>0) {
                     	Contact cont=getContact(user.getId());
                     	if(cont.getAvatar()==null && d.getPhotoData().size()>0) {
                     		searchAvatarFileName(cont,d.getPhotoData());
                     	}
                          cg=new Chat(chatId,cont, cont.getName()+" "+cont.getLastName());
                         

                     }
                 } else if ((chatName=rs.getString("groupName")) != null) {
                     dados = rs.getBytes("dadosGrupo");
                     
                	 d.setDecoderData(dados, DecoderTelegramInterface.CHAT);
                     Contact cont=getContact(chatId);
                     d.getChatData(cont);
                     
                     
                     searchAvatarFileName(cont,d.getPhotoData());
                     
                     cg = new ChatGroup(chatId,cont , chatName);
                     

                 }
                 if(cg!=null) {
                 	System.out.println("Nome do chat "+cg.getId());
                     /*
                 	ArrayList<Message> messages=extractMessages(conn, cg);
                     if(messages == null || messages.isEmpty())
                         continue;
                    */
                     //cg.messages.addAll(messages);
                     l.add(cg);
                 }
            	 
             }
     	}catch (Exception e) {
			// TODO: handle exception
     		e.printStackTrace();
		}
     	return l;
    }
    /*
    protected ArrayList<Chat> extractChatList_old(){
    	ArrayList<Chat> l =new ArrayList<>();
    	System.out.println("parser telegram!!!!!");
    	try {
            PreparedStatement stmt = conn.prepareStatement(CHATS_SQL);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                long chatId = rs.getLong("chatId");
                String chatName = null;
                byte[] dados;
                Chat cg=null;
                if ((chatName=rs.getString("nomeChat")) != null) {
                    dados = rs.getBytes("dadosChat");
                    SerializedData s = new SerializedData(dados);
                    TLRPC.User u = TLRPC.User.TLdeserialize(s, s.readInt32(false), false);
                    
                    if (u!=null) {
                    	Contact cont=getContact(u.id);
                    	if(cont.getAvatar()==null && u.photo!=null) {
                    		searchAvatarFileName(cont,u.photo.photo_big,u.photo.photo_small);
                    	}
                         cg=new Chat(chatId,cont , chatName);
                        //println(u.first_name)

                    }
                } else if ((chatName=rs.getString("groupName")) != null) {
                    dados = rs.getBytes("dadosGrupo");
                    SerializedData s = new SerializedData(dados);
                    TLRPC.Chat c = TLRPC.Chat.TLdeserialize(s, s.readInt32(false), false);
                    Contact cont=getContact(c.id);
                    cont.setName(chatName);
                    
                    searchAvatarFileName(cont,c.photo.photo_big,c.photo.photo_small);
                    
                    cg = new ChatGroup(chatId,cont , chatName);

                }
                if(cg!=null) {
                	System.out.println("Nome do chat "+cg.getId());
                   
                    //cg.messages.addAll(messages);
                    l.add(cg);
                }
            }
        } catch (Exception e ) {
            //log error
        	e.printStackTrace();
        	
        }

        return l;
    }
    
    protected void extractLink(Message message,TLRPC.WebPage webpage) {
    	message.setLink(true);
        message.setMediaMime("link");
        //message.data+="link compartilhado: "+m.media.webpage.display_url
        if(webpage.photo!=null) {
            String img=getFileFromPhoto(webpage.photo.sizes);
            

            if(img!=null){
            	try {
                    message.setLinkImage(FileUtils.readFileToByteArray(new File(img)));
                    message.setMediaMime("link/Image");
            	}catch (Exception e) {
					// TODO: handle exception
				}
            }
           
            
        }
    }
    */
    protected ArrayList<Message> extractMessages(Chat chat) throws Exception{
    	ArrayList<Message> msgs=new ArrayList<Message>();
        PreparedStatement stmt=conn.prepareStatement(EXTRACT_MESSAGES_SQL);
        DecoderTelegramInterface d=(DecoderTelegramInterface)Class.forName(DECODER_CLASS).newInstance();
        if(stmt!=null) {
            stmt.setLong(1,chat.getId());
            ResultSet rs=stmt.executeQuery();
            if(rs!=null) {
                while (rs.next()) {
                	 byte[] data = rs.getBytes ("data");
                	 long mid=rs.getLong("mid");
                	 Message message= new Message(mid,chat);
                	 d.setDecoderData(data, DecoderTelegramInterface.MESSAGE);
                	 d.getMessageData(message);
                	 message.setRemetente(getContact(d.getRemetenteId()));
                	 
                	 if(message.getMediaMime()!=null) {
	                	 if(message.getMediaMime().startsWith("image")) {
	                		 List<PhotoData> list=d.getPhotoData();
	                		 loadImage(message, list);
	                	 }else if(message.getMediaMime().startsWith("link")) {
	                		 loadLink(message, d.getPhotoData());
	                	 }else if(message.getMediaMime().length()>0) {
	                		 loadDocument(message,d.getDocumentNames(),d.getDocumentSize());
	                	 }
                	 }
                	 msgs.add(message);
                }
            }
        }
        return msgs;
    }
    private void loadDocument(Message message,List<String> names,int size) {
    	List<IItemBase> result = null;
    	for(String name:names) {
        	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems(BasicProps.NAME+":"+ "\""+name+"\"",searcher);
            String path=getPathFromResult(result, size);
            if(path!=null){
            	message.setMediaFile(path);
            	message.setMediaHash(getHash(result, size));
            	message.setThumb(getThumb(result, size));
                break;
            }
            
    	}
    }
    private void loadLink(Message message,List<PhotoData> list) {
    	
    	for(PhotoData p:list) {
    		IItemBase r=getFileFrom(p.getName(), p.getSize());
    		if(r!=null) {
    			message.setType("link/image");
    			try {
					message.setLinkImage(FileUtils.readFileToByteArray(r.getTempFile()));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    		}
    	}
    	
    }
    private void loadImage(Message message,List<PhotoData> list) {
    	for(PhotoData p:list) {
    		IItemBase r=getFileFrom(p.getName(), p.getSize());
    		if(r!=null) {
    			message.setThumb(r.getThumb());
    			message.setMediaHash(r.getHash());
    			message.setMediaFile(r.getPath());
    			message.setMediaName(r.getName());
    		}
    	}
    }
    /*
    protected ArrayList<Message> extractMessages_old(Chat chat) throws Exception{
    	ArrayList<Message> msgs=new ArrayList<Message>();
    	        PreparedStatement stmt=conn.prepareStatement(EXTRACT_MESSAGES_SQL);
    	        if(stmt!=null) {
    	            stmt.setLong(1,chat.getId());
    	            ResultSet rs=stmt.executeQuery();
    	            if(rs!=null) {
    	                while (rs.next()) {
    	                    byte[] data = rs.getBytes ("data");
    	                    SerializedData sd = new SerializedData(data);
    	                    int aux = sd.readInt32 (false);
    	                    long mid=rs.getLong("mid");

    	                    TLRPC.Message m = TLRPC.Message.TLdeserialize (sd, aux, false);  	                    

    	                    if (m!=null ) {
    	                    	
    	                        Message message= new Message(mid,chat);
    	                        if(m.action!=null) {
    	                        	if(m.action.call!=null) {
    	                        		message.setType("call duration:"+m.action.duration);
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionChatJoinedByLink) {
    	                        		message.setType("User Join chat by link");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionChatAddUser) {
    	                        		message.setType("Chat Add User");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionUserJoined) {
    	                        		message.setType("User Join");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionHistoryClear) {
    	                        		message.setType("History Clear");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
    	                        		message.setType("User deleted");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionChannelCreate) {
    	                        		message.setType("Channel created");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
    	                        		message.setType("User update photo");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionChatEditPhoto) {
    	                        		message.setType("Chat update photo");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
    	                        		message.setType("Chat delete photo");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionChatEditTitle) {   	                        		
    	                        		message.setType("Change title to "+m.action.title);
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionContactSignUp) {
    	                        		message.setType("Contact sign up");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionChatMigrateTo) {
    	                        		message.setType("Chat migrate");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionPinMessage) {
    	                        		message.setType("Message pinned");
    	                        	}
    	                        	
    	                        	if(message.getType()==null) {
    	                        		System.out.println("tipo "+ReflectionToStringBuilder.toString(m.action));
    	                        	}
    	                        	
    	                        }
    	                        
    	                        
    	                        

    	                        message.setFromMe(rs.getInt("out")==1);

    	                        message.setRemetente(getContact(m.from_id));
    	                        
    	                        
    	                        
    	                       
    	                        message.setData(m.message);
    	                       
    	                        
    	                        message.setTimeStamp(Date.from(Instant.ofEpochSecond(m.date)));
    	                        //message.timeStamp=LocalDateTime.ofInstant(Instant.ofEpochSecond(), ZoneId.systemDefault())
    	                        if(m.media!=null) {
    	                            if(m.media.document!=null) {
    	                                extractDocument(message,m.media.document);
    	                            }

    	                            if(m.media.photo!=null){
    	                            	extractPhoto(message, m.media.photo);

    	                            }
    	                            if(m.media.webpage!=null) {
    	                            	extractLink(message, m.media.webpage);
    	                            	    	                                
    	                            }


    	                            if(message.getMediaFile()!=null){
    	                            	if(message.getMediaHash()==null) {
	    	                            	File f=new File(message.getMediaFile());
	    	                                try {
	    	                                	message.setMediaHash(Util.hashFile(new FileInputStream(f)));
	    	                                }catch(Exception e) {
	    	                                	
	    	                                }
    	                            	}
    	                                
    	                            }else{
    	                                message.setMediaHash(null);
    	                            }

    	                        }
    	                        if(message.getThumb()!=null){
    	                        	String hash=Util.hashFile(new ByteArrayInputStream(message.getThumb()));
    	                            message.setHashThumb(hash);
    	                        }
    	                        msgs.add(message);


    	                    }
    	                    //System.out.println(m.message);

    	                }
    	            }
    	        }

    	        return msgs;
    }
    
   
   
    protected void extractDocument(Message message,TLRPC.Document document) throws IOException {
    	message.setMediaMime(document.mime_type);
    	message.setMediaName(document.id+"");
    	
    	List<IItemBase> result = null;
    	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems(BasicProps.NAME+":\""+ message.getMediaName()+"\"",searcher);
        message.setMediaFile(getPathFromResult(result, document.size));
        message.setMediaHash(getHash(result, document.size));
        message.setThumb(getThumb(result, document.size));
        
    	if(message.getMediaName().contains("5332534912568264569")) {
    		
    		System.out.println("olha o arquivo "+message.getMediaName()) ;
    		
    		System.out.println("hash "+message.getMediaHash());
    		
    	}
    	    	       
        if(message.getMediaFile()==null){
            for( DocumentAttribute at :document.attributes){
                //tentar achar pelo nome do arquivo original
                if(at.file_name!=null){
                	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems(BasicProps.NAME+":"+ "\""+at.file_name+"\"",searcher);
                    String path=getPathFromResult(result, document.size);
                    if(path!=null){
                    	message.setMediaFile(path);
                    	message.setMediaHash(getHash(result, document.size));
                    	message.setThumb(getThumb(result, document.size));
                        break;
                    }
                }
            }
        }


        if(message.getThumb()==null && document.thumbs!=null && document.thumbs.size()>0){
            String file=getFileFromPhoto(document.thumbs);
            if(file!=null) {
                message.setThumb(FileUtils.readFileToByteArray(new File(file)));
            }
        }
    	
    }
    
    protected void extractPhoto(Message message, TLRPC.Photo photo) {
    	message.setMediaMime("image/jpeg");
        if(photo.sizes.size()>0) {
        	message.setMediaFile(getFileFromPhoto(photo.sizes));
            
        }
    }
    */
    private IItemSearcher searcher;
    public void setSearcher(IItemSearcher s) {
    	searcher=s;
    }
    
    
    protected String getPathFromResult(List<IItemBase> result,int size) {
    	if(result==null) {
    		return null;
    	}
    	for(IItemBase f:result) {
    		try {
				if(f.getTempFile()!=null && f.getTempFile().getAbsoluteFile().length()==size) {
					if(f.getFile()!=null) {						
						return f.getFile().getAbsolutePath();				    	
					}else {
						return f.getTempFile().getAbsolutePath();
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	return null;
	}
    protected String getHash(List<IItemBase> result,int size) {
    	if(result==null)
    		return null;
    	for(IItemBase f:result) {
    		try {
				if(f.getTempFile()!=null) {
					if(f.getTempFile().getAbsoluteFile().length()==size) {
						return f.getHash();
					}	
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	return null;
	}
    
    protected byte[] getThumb(List<IItemBase> result,int size) {
    	if(result==null)
    		return null;
    	for(IItemBase f:result) {
    		if(f.getLength()==size) {
					return f.getThumb();
    		}
    	}
    	return null;
	}
    
    private IItemBase  getFileFrom(String name,int size) {
    	List<IItemBase> result = null;
    	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems(BasicProps.NAME+":\""+ name+"\" && size:"+size,searcher);
    	if(result!=null && !result.isEmpty()) {
    		return result.get(0);
    	}
    	return null;
    }
    /*
    private String getFileFromPhoto(ArrayList<PhotoSize> sizes) {
    	List<IItemBase> result = null;
    	
	    for(TLRPC.PhotoSize img:sizes) {
	    	if(img.location==null) {
	    		continue;
	    	}
	    	String name=""+img.location.volume_id+"_"+img.location.local_id;
	    	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems(BasicProps.NAME+":\""+ name+".jpg\"  - size:0",searcher);
	    	
            if(result==null || result.isEmpty()){
            	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems(BasicProps.NAME+":\""+ name+"\"  - size:0",searcher);
            }
            if(result!=null){
            	return getPathFromResult(result, img.size);
            	
            }
	    }
       
        return null;
		
	}
	*/

	protected void extractContacts() throws SQLException {
		DecoderTelegramInterface d=null;
		try {
			Object o=Class.forName(DECODER_CLASS).newInstance();
			d=(DecoderTelegramInterface)o;
			System.out.println(ReflectionToStringBuilder.toString(o));
		}catch (Exception e) {
			System.out.println("erro ao carregar");
			// TODO: handle exception
			return;
			
		}
		if(conn!=null) {
            PreparedStatement stmt = conn.prepareStatement(EXTRACT_CONTACTS_SQL);
            if(stmt!=null){
                ResultSet rs= stmt.executeQuery();
                if(rs==null)
                	return;
                int nphones=0;
                while (rs.next()){
                	d.setDecoderData(rs.getBytes("data"), DecoderTelegramInterface.USER);
                	Contact c=new Contact(0);
                	d.getUserData(c);
                	//SerializedData s= new SerializedData();
                	//TLRPC.User user=TLRPC.User.TLdeserialize(s,s.readInt32(false),false);
                    //Contact cont=
                	if(c.getId()>0){
                        Contact cont=getContact(c.getId());
                        if(cont.getName()==null) {
                        	cont.setName(c.getName());
                        	cont.setLastName(c.getLastName());
                        	cont.setUsername(c.getUsername());
                        	cont.setPhone(c.getPhone());
                        }
                        
                        if(cont.getPhone()!=null) {
                        	nphones++;
                        }
                        List<PhotoData> photo=d.getPhotoData();
                        if(cont.getAvatar()!=null &&  photo.size()>0){
                        	try {
                        		if(cont.getPhone()!=null)
                        			searchAvatarFileName(cont,photo);
                        	}catch (IOException e) {
                        		// TODO: handle exception
                        		e.printStackTrace();
							}
                        }
                    }
                }
                System.out.println("tot_phones "+nphones);
                
            }
        }
    	
    }
    
	protected void searchAvatarFileName(Contact contact,List<PhotoData> photos) throws IOException {
		 List<IItemBase> result=null;
		 String name=null;
		for(PhotoData photo:photos ) {
			if(photo.getName()!=null) {
				name=photo.getName()+".jpg";
		    	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems(BasicProps.NAME+":\""+ name+"\"  - "+BasicProps.LENGTH+":0",searcher);
		    	if(result!=null && !result.isEmpty()) {
		    		break;
		    	}
			}
		}
		if(result!=null && !result.isEmpty()) {
        	File f=result.get(0).getTempFile().getAbsoluteFile();
        	System.out.println("avatar " +name);
        	System.out.println("arq "+f.getName());
            contact.setAvatar(FileUtils.readFileToByteArray(f));
        }
	}
    
	/*
    protected void searchAvatarFileName(Contact contact,TLRPC.FileLocation big,TLRPC.FileLocation small) throws IOException {
        List<IItemBase> result=null;
        int size=0;
        String name=null;
        if(big!=null){
        	name=""+ big.volume_id + "_" + big.local_id+".jpg";
	    	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems(BasicProps.NAME+":\""+ name+"\"  - "+BasicProps.LENGTH+":0",searcher);
            
           
        }
        if((result==null || result.isEmpty()) && small!=null){
        	name=""+ small.volume_id + "_" + small.local_id+".jpg";
        	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems(BasicProps.NAME+":\""+ name+"\" - "+BasicProps.LENGTH+":0",searcher);
           
        }
        if(result!=null && !result.isEmpty()) {
        	File f=result.get(0).getTempFile().getAbsoluteFile();
        	System.out.println("avatar " +name);
        	System.out.println("arq "+f.getName());
            contact.setAvatar(FileUtils.readFileToByteArray(f));
        }
        
    }
    */
    
    protected Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
    }


    public ArrayList<Chat> getChatList() {
		return chatList;
	}
	public HashMap<Long, Contact> getContacts() {
		return contacts;
	}
	private static final String CHATS_SQL ="SELECT d.did as chatId,u.name as nomeChat,u.data as dadosChat,"
    		+ "c.name as groupName, c.data as dadosGrupo "
    		+ "from dialogs d LEFT join users u on u.uid=d.did LEFT join chats c on -c.uid=d.did "
    		+ "order by d.date desc";
    
    private static final String EXTRACT_MESSAGES_SQL="SELECT m.*,md.data as mediaData FROM messages m  "
    		+ "left join media_v2 md on md.mid=m.mid where m.uid=? order by date";
        


    private static final String EXTRACT_CONTACTS_SQL="SELECT * FROM users";
   
    private static final String DECODER_CLASS="telegramdecoder.DecoderTelegram";
    
    

}