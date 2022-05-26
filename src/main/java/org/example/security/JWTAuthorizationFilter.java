package org.example.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import org.example.accessor.AuthAccessor;
import org.example.accessor.UserAccessor;
import org.example.accessor.models.AuthDTO;
import org.example.accessor.models.UserDTO;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

public class JWTAuthorizationFilter extends BasicAuthenticationFilter {

//not autowiring these two becoz we will create a bin for these not spring
    private AuthAccessor authAccessor;
    private UserAccessor userAccessor;

    public JWTAuthorizationFilter(AuthenticationManager authenticationManager){
        super(authenticationManager);
    }

    @Override
    public void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        ServletContext servletContext=req.getServletContext();
        WebApplicationContext applicationContext= WebApplicationContextUtils.getWebApplicationContext(servletContext);

        /** creating beans manually**/
        if(userAccessor==null){
            userAccessor=(UserAccessor) applicationContext.getBean("userAccessor");
        }
        if(authAccessor==null){
            authAccessor=(AuthAccessor) applicationContext.getBean("authAccessor");
        }
        try {
            UsernamePasswordAuthenticationToken authentication = getAuthentication(req);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(req, res);
        }
        catch(MalformedJwtException| BadCredentialsException ex){
            res.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }
    }

    private UsernamePasswordAuthenticationToken getAuthentication(HttpServletRequest req){
        String authorizationHeader=req.getHeader("Authorization");
        final String tokenPrefix="Bearer ";
        if(authorizationHeader!=null){
            if(authorizationHeader.startsWith(tokenPrefix)){
               String token= authorizationHeader.replace(tokenPrefix,"");

                //decoding the token
                Claims claims= Jwts.parser()
                        .setSigningKey(SecurityConstants.SECRET_KEY.getBytes())
                        .parseClaimsJws(token)
                        .getBody();
                Date expirationTime=claims.getExpiration();
                if(expirationTime.after(new Date(System.currentTimeMillis()))){
                    AuthDTO authDTO=authAccessor.getAuthByToken(token);
                    if(authDTO!=null){
                        UserDTO userDTO=userAccessor.getUserByEmail(claims.getSubject());
                        if(userDTO!=null){
                            return new UsernamePasswordAuthenticationToken(userDTO,userDTO.getPassword(),
                                    Arrays.asList(new SimpleGrantedAuthority(userDTO.getRole().name())));
                        }
                    }
                }
            }
        }
        return new UsernamePasswordAuthenticationToken(null,null,Arrays.asList(new SimpleGrantedAuthority(Roles.Anonymous)));
    }
}
