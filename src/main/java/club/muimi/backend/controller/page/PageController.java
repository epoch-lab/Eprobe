package club.muimi.backend.controller.page;

import club.muimi.backend.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    /**
     * 统一将前端页面路由转发到单页入口。
     * 后续接入 Vue 构建产物时，只需要替换 static/index.html 与对应静态资源即可。
     */
    @GetMapping({
            "/",
            "/{path:^(?!api$|error$|assets$)[^.]+}",
            "/{path:^(?!api$|error$|assets$)[^.]+}/{*remaining}"
    })
    public String forwardToIndex(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.contains(".")) {
            throw new NotFoundException("页面不存在");
        }
        return "forward:/index.html";
    }
}
